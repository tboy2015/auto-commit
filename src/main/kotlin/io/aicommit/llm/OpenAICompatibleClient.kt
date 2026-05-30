package io.aicommit.llm

import io.aicommit.settings.Provider
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAICompatibleClient(
    private val provider: Provider,
    private val apiKey: String?,
    private val httpFactory: () -> OkHttpClient = { defaultHttp(provider) },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun stream(messages: List<ChatMessage>): Flow<String> = flow {
        val req = ChatRequest(
            model = provider.model,
            messages = messages,
            stream = true,
            temperature = provider.temperature,
            maxTokens = provider.maxTokens,
        )
        val body = json.encodeToString(ChatRequest.serializer(), req)
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body)
            .header("Accept", "text/event-stream")
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val call = httpFactory().newCall(builder.build())
        // Cancel the in-flight HTTP call as soon as the coroutine is cancelled,
        // so blocking SSE reads unblock immediately.
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val response = try { call.execute() } catch (e: IOException) {
            if (call.isCanceled()) throw kotlinx.coroutines.CancellationException("cancelled")
            throw LLMException.Network(describeNetworkError(provider, e), e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw when (resp.code) {
                    401, 403 -> LLMException.Auth("auth failed: ${resp.code}")
                    429 -> LLMException.RateLimited("rate limited")
                    400 -> if (errBody.contains("context", true) || errBody.contains("length", true))
                        LLMException.ContextTooLong(errBody) else LLMException.BadResponse(errBody)
                    else -> LLMException.BadResponse("http ${resp.code}: ${errBody.take(500)}")
                }
            }
            // 防御：200 但返回的不是 SSE（例如错误 JSON）。读整段 body 抛出。
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            if (contentType.isNotEmpty()
                && !contentType.contains("event-stream")
                && !contentType.contains("text/plain")
            ) {
                val body = resp.body?.string().orEmpty()
                log.warn("non-SSE 200 response (content-type=$contentType): ${body.take(500)}")
                throw LLMException.BadResponse("非流式响应 (content-type=$contentType): ${body.take(300)}")
            }
            val source = resp.body?.source() ?: throw LLMException.BadResponse("empty body")
            var sawContent = false
            var sawReasoning = false
            var reasoningChars = 0
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trimEnd('\r')
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (!trimmed.startsWith("data:")) {
                    log.debug("non-data SSE line: ${trimmed.take(200)}")
                    continue
                }
                val payload = trimmed.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull()
                if (chunk == null) {
                    log.debug("could not parse SSE payload: ${payload.take(300)}")
                    continue
                }
                val delta = chunk.choices.firstOrNull()?.delta ?: continue
                val content = delta.content
                if (!content.isNullOrEmpty()) {
                    sawContent = true
                    emit(content)
                } else if (!delta.reasoningContent.isNullOrEmpty()) {
                    // 推理模型：吞掉 reasoning_content，等待正式 content
                    sawReasoning = true
                    reasoningChars += delta.reasoningContent.length
                }
            }
            if (!sawContent && sawReasoning) {
                log.warn("stream returned only reasoning_content ($reasoningChars chars); model is reasoning-only or never reached content phase.")
                throw LLMException.BadResponse(
                    "模型只返回了推理内容（reasoning），没有正文。请尝试切换到该服务的快速/非思考模型，例如 DeepSeek 的 deepseek-v4-flash；如果当前模型支持关闭 Thinking，请在服务端关闭后重试。"
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    fun listModels(): List<String> {
        val builder = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/models")
            .get()
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val response = try { httpFactory().newCall(builder.build()).execute() }
        catch (e: IOException) { throw LLMException.Network(describeNetworkError(provider, e), e) }
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw when (resp.code) {
                    401, 403 -> LLMException.Auth("auth failed: ${resp.code}")
                    429 -> LLMException.RateLimited("rate limited")
                    else -> LLMException.BadResponse("http ${resp.code}: ${body.take(500)}")
                }
            }
            val parsed = runCatching { json.decodeFromString(ModelListResponse.serializer(), body) }
                .getOrElse { throw LLMException.BadResponse("invalid /models response: ${body.take(200)}") }
            return parsed.data.map { it.id }.sorted()
        }
    }

    companion object {
        private val log = Logger.getInstance(OpenAICompatibleClient::class.java)

        /** 把底层 IOException 包装成用户能看懂的提示，附带代理上下文。 */
        internal fun describeNetworkError(p: Provider, e: Throwable): String {
            val proxyDesc = when {
                p.proxyUrl.isNotBlank() -> "通过独立代理 ${p.proxyUrl}"
                else -> ideGlobalProxy(p.baseUrl)?.let { "通过 IDE 全局代理 $it" } ?: "未走代理"
            }
            val hint = when (e) {
                is javax.net.ssl.SSLHandshakeException,
                is javax.net.ssl.SSLException ->
                    "TLS 握手失败。可能原因：① 代理端口/服务未启动 ② 代理不支持 HTTPS CONNECT ③ 目标域名被代理拦截 ④ 系统时间不同步"
                is java.net.ConnectException ->
                    "无法建立 TCP 连接。检查端口号是否正确、代理服务是否在运行（macOS: lsof -i :端口）"
                is java.net.SocketTimeoutException ->
                    "超时。代理可能未转发到目标，或目标 API 响应慢；可调大 Timeout"
                is java.net.UnknownHostException ->
                    "DNS 解析失败：${e.message}。检查域名拼写或代理是否提供 DNS"
                else -> e.message.orEmpty()
            }
            return buildString {
                append("network error: ${e.javaClass.simpleName}")
                if (e.message != null) append(" — ${e.message}")
                append("\n→ $proxyDesc → ${p.baseUrl}")
                if (hint.isNotBlank()) append("\n💡 $hint")
            }
        }

        fun defaultHttp(p: Provider): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
                .writeTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            resolveProxy(p)?.let {
                log.info("using proxy=$it for ${p.baseUrl}")
                builder.proxy(it)
            }
            return builder.build()
        }

        /** 默认跟随 IDE 全局；provider.proxyUrl 非空时为"独立代理"，覆盖全局。 */
        private fun resolveProxy(p: Provider): java.net.Proxy? {
            if (p.proxyUrl.isNotBlank()) {
                return parseExplicitProxy(p.proxyUrl) ?: run {
                    log.warn("invalid proxyUrl='${p.proxyUrl}', falling back to IDE global")
                    ideGlobalProxy(p.baseUrl)
                }
            }
            return ideGlobalProxy(p.baseUrl)
        }

        private fun parseExplicitProxy(raw: String): java.net.Proxy? = runCatching {
            val u = java.net.URI(raw.trim())
            val host = u.host ?: return@runCatching null
            val port = if (u.port > 0) u.port else 80
            val scheme = u.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@runCatching null
            java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port))
        }.getOrNull()

        private fun ideGlobalProxy(targetUrl: String): java.net.Proxy? = runCatching {
            val selected = java.net.ProxySelector.getDefault()?.select(java.net.URI(targetUrl)).orEmpty()
            selected.firstOrNull { it.type() != java.net.Proxy.Type.DIRECT }
        }.getOrNull()
    }
}
