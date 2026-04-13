package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class ChatCompletionMessageToolCall {
  abstract val id: String
}

@Serializable
@SerialName("function")
data class ChatCompletionMessageFunctionToolCall(
  override val id: String,
  val function: FunctionCall,
) : ChatCompletionMessageToolCall()

@Serializable
data class FunctionCall(
  val name: String,
  val arguments: String
)
