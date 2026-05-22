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
            throw LLMException.Network("network error: ${e.message}", e)
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
                    "模型只返回了推理内容（reasoning），没有正文。请换用非推理模型，如 deepseek-chat。"
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
        catch (e: IOException) { throw LLMException.Network("network error: ${e.message}", e) }
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
        fun defaultHttp(p: Provider): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
    }
}
