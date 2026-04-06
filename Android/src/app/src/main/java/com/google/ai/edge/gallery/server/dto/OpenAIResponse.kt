package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponse(
  val id: String,
  val `object`: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<Choice>,
)

@Serializable
data class Choice(
  val index: Int,
  val message: ChatMessage? = null,      // For non-streaming
  val delta: Delta? = null,              // For streaming
  val finish_reason: String? = null,
)

@Serializable
data class Delta(
  val role: String? = null,
  val content: String? = null,
)

// SSE Stream Chunk
@Serializable
data class ChatCompletionChunk(
  val id: String,
  val `object`: String = "chat.completion.chunk",
  val created: Long,
  val model: String,
  val choices: List<Choice>,
)

@Serializable
data class ModelListResponse(
    val `object`: String = "list",
    val data: List<ModelResponse>
)

@Serializable
data class ModelResponse(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    val owned_by: String = "local"
)