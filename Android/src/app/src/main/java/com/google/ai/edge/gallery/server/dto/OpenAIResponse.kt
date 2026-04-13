package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoResponse(
  val name: String,
  val version: String,
  val model: String?,
  val is_inferring: Boolean,
  val request_count: Int,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatCompletionResponse(
  val id: String,
  @EncodeDefault
  val `object`: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<Choice>,
)

// SSE Stream Chunk
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatCompletionChunk(
  val id: String,
  @EncodeDefault
  val `object`: String = "chat.completion.chunk",
  val created: Long,
  val model: String,
  val choices: List<Choice>,
)

@Serializable
data class Choice(
  val index: Int,
  val message: ChatCompletionMessage? = null,      // For non-streaming
  val delta: Delta? = null,              // For streaming
  val finish_reason: String? = null,
)

@Serializable
data class ChatCompletionMessage(
  val role: String,
  val content: String? = null,
  val tool_calls: List<ChatCompletionMessageToolCall>? = null,
)

@Serializable
data class Delta(
  val role: String? = null,
  val content: String? = null,
  val tool_calls: List<ChatCompletionMessageToolCall>? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ModelListResponse(
  @EncodeDefault
  val `object`: String = "list",
  val data: List<ModelResponse>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ModelResponse(
  val id: String,
  @EncodeDefault
  val `object`: String = "model",
  val created: Long,
  @EncodeDefault
  val owned_by: String = "local"
)
