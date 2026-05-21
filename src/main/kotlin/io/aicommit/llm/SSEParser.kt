package io.aicommit.llm

import kotlinx.serialization.json.Json

object SSEParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(lines: Sequence<String>, onChunk: (String) -> Unit) {
        for (raw in lines) {
            val line = raw.trimEnd('\r')
            if (line.isEmpty() || line.startsWith(":")) continue
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") return
            val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
            chunk.choices.firstOrNull()?.delta?.content?.let(onChunk)
        }
    }
}
