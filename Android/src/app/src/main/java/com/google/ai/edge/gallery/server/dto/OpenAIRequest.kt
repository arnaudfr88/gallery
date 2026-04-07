package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean = false,
)

@Serializable
data class ChatMessage(
  val role: String,   // "system" | "user" | "assistant"
  val content: String,
)
