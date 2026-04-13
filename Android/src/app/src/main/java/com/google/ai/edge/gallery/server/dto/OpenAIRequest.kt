package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatCompletionMessageParam>,
  val stream: Boolean = false,
  val temperature: Float? = null,
  val top_p: Float? = null,
  val seed: Int? = null,
  val tools: List<ChatCompletionTool>? = null,
  val extra_body: Map<String, JsonElement>? = null,
)

@Serializable
data class ChatCompletionMessageParam(
  val role: String,   // "system" | "user" | "assistant" | "tool"
  val content: JsonElement? = null,
  val tool_calls: List<ChatCompletionMessageToolCall>? = null,  // when role is "assistant"
  val tool_call_id: String? = null, // when role is "tool"
) {
  /**
   * Returns the text content if it's a simple string, or concatenates all text parts
   * if it's an array of content parts.
   */
  val textContent: String
    get() = when (content) {
      is JsonArray -> {
        content.mapNotNull { element ->
          if (element is JsonObject) {
            val type = element["type"]?.jsonPrimitive?.content
            if (type == "text") {
              element["text"]?.jsonPrimitive?.content
            } else {
              null
            }
          } else {
            null
          }
        }.joinToString("\n")
      }
      is JsonPrimitive -> content.jsonPrimitive.content
      else -> ""
    }
}

@Serializable
sealed class ContentPart {
  @Serializable
  @SerialName("text")
  data class TextContentPart(
    val text: String
  ) : ContentPart()

  @Serializable
  @SerialName("image_url")
  data class ImageUrlContentPart(
    @SerialName("image_url") val imageUrl: ImageUrl
  ) : ContentPart()

  @Serializable
  @SerialName("input_audio")
  data class InputAudioContentPart(
    @SerialName("input_audio") val inputAudio: InputAudio
  ) : ContentPart()

  @Serializable
  data class ImageUrl(
    val url: String,
    val detail: ImageDetail? = ImageDetail.AUTO
  )

  @Serializable
  data class InputAudio(
    val data: String, // Base64 encoded audio data
    val format: String // "wav" or "mp3"
  )
}

@Serializable
enum class ImageDetail {
    @SerialName("low") LOW,
    @SerialName("auto") AUTO,
    @SerialName("high") HIGH
}

@Serializable
sealed class ChatCompletionTool {
}

@Serializable
@SerialName("function")
data class ChatCompletionFunctionTool(
  val function: FunctionDefinition
) : ChatCompletionTool()

@Serializable
data class FunctionDefinition(
  val name: String,
  val description: String? = null,
  val parameters: JsonObject? = null,
)
