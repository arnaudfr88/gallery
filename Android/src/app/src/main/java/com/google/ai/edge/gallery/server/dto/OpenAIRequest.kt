package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean = false,
  val temperature: Float? = null,
  val max_tokens: Int? = null,
)

@Serializable
data class ChatMessage(
  val role: String,   // "system" | "user" | "assistant"
  val content: String,
)
