package io.aicommit.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 512,
)

@Serializable
data class StreamDelta(val content: String? = null)

@Serializable
data class StreamChoice(val delta: StreamDelta = StreamDelta(), val index: Int = 0)

@Serializable
data class StreamChunk(val choices: List<StreamChoice> = emptyList())

@Serializable
data class ModelInfo(val id: String)

@Serializable
data class ModelListResponse(val data: List<ModelInfo> = emptyList())
