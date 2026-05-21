package io.aicommit.llm

import io.aicommit.settings.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        val response = try { call.execute() } catch (e: IOException) {
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
            val source = resp.body?.source() ?: throw LLMException.BadResponse("empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trimEnd('\r')
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (!trimmed.startsWith("data:")) continue
                val payload = trimmed.removePrefix("data:").trim()
                if (payload == "[DONE]") return@use
                val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
                chunk.choices.firstOrNull()?.delta?.content?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun defaultHttp(p: Provider): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
    }
}
