package com.google.ai.edge.gallery.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val stream: Boolean = false,
  val temperature: Float? = null,
  val top_p: Float? = null,
  val seed: Int? = null,
  val extra_body: Map<String, JsonElement>? = null,
)

@Serializable
data class ChatMessage(
  val role: String,   // "system" | "user" | "assistant"
  val content: JsonElement,
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
      else -> content.jsonPrimitive.content
    }
}

@Serializable
sealed class ContentPart {
  abstract val type: String

  @Serializable
  @SerialName("text")
  data class TextContentPart(
    override val type: String = "text",
    val text: String
  ) : ContentPart()

  @Serializable
  @SerialName("image_url")
  data class ImageUrlContentPart(
    override val type: String = "image_url",
    @SerialName("image_url") val imageUrl: ImageUrl
  ) : ContentPart()

  @Serializable
  @SerialName("input_audio")
  data class InputAudioContentPart(
    override val type: String = "input_audio",
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
