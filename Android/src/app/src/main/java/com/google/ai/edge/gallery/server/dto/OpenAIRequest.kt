package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean = false,
  val extra_body: Map<String, JsonElement>? = null,
)

@Serializable
data class ChatMessage(
  val role: String,   // "system" | "user" | "assistant"
  val content: String,
)
