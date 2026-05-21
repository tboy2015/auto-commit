package io.aicommit.settings

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double = 0.3,
    val maxTokens: Int = 512,
    val timeoutSec: Int = 60,
    val extraHeaders: Map<String, String> = emptyMap(),
)
