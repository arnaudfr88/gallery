/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import androidx.core.graphics.scale
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import com.google.ai.edge.gallery.server.dto.ChatCompletionChunk
import com.google.ai.edge.gallery.server.dto.ChatCompletionFunctionTool
import com.google.ai.edge.gallery.server.dto.ChatCompletionMessage
import com.google.ai.edge.gallery.server.dto.ChatCompletionMessageFunctionToolCall
import com.google.ai.edge.gallery.server.dto.ChatCompletionMessageParam
import com.google.ai.edge.gallery.server.dto.ChatCompletionMessageToolCall
import com.google.ai.edge.gallery.server.dto.ChatCompletionRequest
import com.google.ai.edge.gallery.server.dto.ChatCompletionResponse
import com.google.ai.edge.gallery.server.dto.Choice
import com.google.ai.edge.gallery.server.dto.Delta
import com.google.ai.edge.gallery.server.dto.FunctionCall
import com.google.ai.edge.gallery.server.dto.ImageDetail
import com.google.ai.edge.gallery.server.dto.ModelListResponse
import com.google.ai.edge.gallery.server.dto.ModelResponse
import com.google.ai.edge.gallery.server.dto.ServerInfoResponse
import com.google.ai.edge.gallery.service.ModelManagerAccessor
import com.google.ai.edge.gallery.ui.apiserver.ApiServerStatus
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "AGOpenAIApiServer"

class OpenAIApiServer(
  private val context: Context,
  private val modelManager: ModelManagerAccessor,
  val port: Int = 8080,
  val host: String = "0.0.0.0",
) {
  private var server: ApplicationEngine? = null
  private val json = Json { ignoreUnknownKeys = true }
  private val inferenceMutex = Mutex()

  fun start() {
    server =
      embeddedServer(CIO, port = port, host = host) {
        install(ContentNegotiation) { json(json) }

        routing {
          // Health check
          get("/") {
            val versionName = BuildConfig.VERSION_NAME

            val initializedModel = modelManager.getDownloadedModelNames()
              .mapNotNull { modelManager.getModelByName(it) }
              .find { it.instance != null }
              ?.name

            call.respond(
              ServerInfoResponse(
                name = "AI Edge Gallery OpenAI API Server",
                version = versionName,
                model = initializedModel,
                is_inferring = ApiServerStatus.isInferring.value,
                request_count = ApiServerStatus.requestCount.value
              )
            )
          }

          // List models
          get("/v1/models") {
            val models =
              modelManager.getDownloadedModelNames().map { modelName ->
                ModelResponse(id = modelName, created = System.currentTimeMillis() / 1000)
              }
            call.respond(ModelListResponse(data = models))
          }

          // Chat completions
          post("/v1/chat/completions") {
            ApiServerStatus.incrementRequestCount()
            try {
              val request = call.receive<ChatCompletionRequest>()

              // Find the corresponding downloaded model
              val model = modelManager.getModelByName(request.model)
              if (model == null || model.instance == null) {
                call.respond(
                  HttpStatusCode.NotFound,
                  mapOf(
                    "error" to
                      mapOf("message" to "Model '${request.model}' not found or not initialized")
                  ),
                )
                return@post
              }

              // Process last user message or tools messages
              val lastMessage = request.messages.lastOrNull()
              var userInput = lastMessage?.textContent ?: ""

              val images = mutableListOf<Bitmap>()
              val audioClips = mutableListOf<ByteArray>()
              val toolResponses = mutableMapOf<String, String>()

              if (lastMessage != null && lastMessage.role == "user") {
                // Parse multimedia content from user message
                val content = lastMessage.content
                if (content is JsonArray) {
                  for (element in content) {
                    if (element is JsonObject) {
                      val type = element["type"]?.jsonPrimitive?.content
                      when (type) {
                        "text" -> {
                          userInput = element["text"]?.jsonPrimitive?.content ?: ""
                        }

                        "image_url" -> {
                          if (model.llmSupportImage) {
                            val imageUrlObj = element["image_url"] as? JsonObject
                            val url = imageUrlObj?.get("url")?.jsonPrimitive?.content
                            val detailStr = imageUrlObj?.get("detail")?.jsonPrimitive?.content
                            val detail =
                              when (detailStr) {
                                "low" -> ImageDetail.LOW
                                "high" -> ImageDetail.HIGH
                                else -> ImageDetail.AUTO
                              }

                            if (url != null) {
                              val bitmap = loadBitmap(url)
                              if (bitmap != null) {
                                val resizedBitmap = resizeIfNeeded(bitmap, getResizeConfig(detail))
                                images.add(resizedBitmap)
                              }
                            }
                          }
                        }

                        "input_audio" -> {
                          if (model.llmSupportAudio) {
                            val inputAudioObj = element["input_audio"] as? JsonObject
                            val data = inputAudioObj?.get("data")?.jsonPrimitive?.content
                            if (data != null) {
                              val decodedBytes = Base64.decode(data, Base64.DEFAULT)
                              val monoPcm = decodeToMonoPcm(decodedBytes)
                              if (monoPcm != null) {
                                audioClips.add(wrapPcmInWav(monoPcm, SAMPLE_RATE))
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              } else if (lastMessage != null && lastMessage.role == "tool") {
                // TODO The model seems unable to see toolResponses(Content.ToolResponse),
                //  so the result is temporarily appended to Contents.
                userInput = "After the tool is invoked, it can now answer the user's question. " +
                  "Below is the function's return value.\n"
                // Extract all tool responses from the last assistant tool_calls message
                val lastAssistantToolIndex =
                  request.messages.indexOfLast { it.role == "assistant" && it.tool_calls != null }
                if (lastAssistantToolIndex != -1) {
                  val assistantToolMsg = request.messages[lastAssistantToolIndex]
                  val toolCalls = assistantToolMsg.tool_calls ?: emptyList()

                  // tool_call_id -> function.name
                  val toolCallMap: Map<String, String> =
                    toolCalls.associate { tc ->
                      when (tc) {
                        is ChatCompletionMessageFunctionToolCall ->
                          tc.id to tc.function.name
                      }
                    }

                  // collect tool responses
                  for (i in lastAssistantToolIndex + 1 until request.messages.size) {
                    val msg = request.messages[i]
                    if (msg.role != "tool") continue

                    val toolCallId = msg.tool_call_id
                    val functionName = toolCallMap[toolCallId]

                    if (functionName != null && msg.textContent.isNotEmpty()) {
                      // TODO I'm not quite sure whether the name in Content.ToolResponse is
                      //  the function name or the call ID.
                      //  I've tried both, but the model doesn't seem to show any results.
                      toolResponses["${toolCallId}:${functionName}"] = msg.textContent
                      // TODO The model seems unable to see toolResponses(Content.ToolResponse),
                      //  so the result is temporarily appended to Contents.
                      userInput = "${userInput}\n${toolCallId}:${functionName}: ${msg.textContent}\n"
                    }
                  }
                }
              }

              val allowThinking = model.llmSupportThinking
              val extraBodyEnableThinking =
                request.extra_body?.get("enable_thinking")?.jsonPrimitive?.booleanOrNull
              val enableThinking =
                allowThinking &&
                  (extraBodyEnableThinking
                    ?: model.getBooleanConfigValue(
                      key = ConfigKeys.ENABLE_THINKING,
                      defaultValue = false,
                    ))
              val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

              // Process tools
              val tools = request.tools?.map { tool ->
                when (tool) {
                  is ChatCompletionFunctionTool -> {
                    val definition = tool.function
                    tool(object : OpenApiTool {
                      override fun getToolDescriptionJsonString(): String {
                        val toolObj = JsonObject(mapOf(
                          "name" to JsonPrimitive(definition.name),
                          "description" to JsonPrimitive(definition.description ?: ""),
                          "parameters" to (definition.parameters ?: JsonObject(emptyMap()))
                        ))
                        return json.encodeToString(toolObj)
                      }
                      override fun execute(paramsJsonString: String): String {
                        throw NotImplementedError("The API server currently does not support server-side tool execution. Tools are meant to be handled by the client or engine orchestration.")
                      }
                    })
                  }
                }
              } ?: listOf()

              val requestId = "chatcmpl-${UUID.randomUUID()}"
              val created = System.currentTimeMillis() / 1000

              if (request.stream) {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                  val channel = Channel<String>(Channel.BUFFERED)
                  val toolCallsChannel = Channel<List<ToolCall>>(Channel.BUFFERED)
                  val inferenceDone = CompletableDeferred<Unit>()

                  CoroutineScope(Dispatchers.Default).launch {
                    inferenceMutex.withLock {
                      val initialMessages = buildInitialMessages(request.messages)

                      LlmChatModelHelper.newConversation(
                        model = model,
                        supportImage = model.llmSupportImage,
                        supportAudio = model.llmSupportAudio,
                        systemInstruction =
                          request.messages.firstOrNull { it.role == "system" }?.let { Contents.of(Content.Text(it.textContent)) },
                        initialMessages = initialMessages,
                        tools = tools,
                        enableConversationConstrainedDecoding = false,
                        temperature = request.temperature,
                        topP = request.top_p,
                        seed = request.seed,
                      )
                      delay(500)

                      ApiServerStatus.setInferring(true)
                      LlmChatModelHelper.runInference(
                        model = model,
                        input = userInput,
                        toolCallListener = { toolCalls ->
                          toolCallsChannel.trySend(toolCalls)
                        },
                        resultListener = { partial, done, thinking ->
                          if (partial.startsWith("<ctrl")) {
                            // Do nothing. Ignore control tokens.
                          } else {
                            if (!thinking.isNullOrEmpty()) {
                              channel.trySend(thinking)
                            }
                            if (partial.isNotEmpty()) {
                              channel.trySend(partial)
                            }
                          }
                          if (done) {
                            ApiServerStatus.setInferring(false)
                            inferenceDone.complete(Unit)
                          }
                        },
                        cleanUpListener = {
                          ApiServerStatus.setInferring(false)
                          if (!inferenceDone.isCompleted) inferenceDone.complete(Unit)
                        },
                        onError = { error ->
                          ApiServerStatus.setInferring(false)
                          inferenceDone.completeExceptionally(Exception(error))
                        },
                        images = images,
                        audioClips = audioClips,
                        toolResponses = toolResponses,
                        extraContext = extraContext,
                      )

                      try {
                        inferenceDone.await()
                      } catch (e: Exception) {
                        Log.e(TAG, "Inference error", e)
                      } finally {
                        channel.close()
                        toolCallsChannel.close()
                      }
                    }
                  }

                  // Send role delta
                  val roleChunk =
                    ChatCompletionChunk(
                      id = requestId,
                      created = created,
                      model = request.model,
                      choices = listOf(Choice(index = 0, delta = Delta(role = "assistant"))),
                    )
                  write("data: ${json.encodeToString(roleChunk)}\n\n")
                  flush()

                  // Send tokens
                  for (token in channel) {
                    val chunk =
                      ChatCompletionChunk(
                        id = requestId,
                        created = created,
                        model = request.model,
                        choices = listOf(Choice(index = 0, delta = Delta(content = token))),
                      )
                    write("data: ${json.encodeToString(chunk)}\n\n")
                    flush()
                  }

                  // Send tool calls if any
                  var hasToolCalls = false
                  for (toolCalls in toolCallsChannel) {
                     hasToolCalls = true
                     val toolCallsDto = toolCalls.map { tc ->
                        ChatCompletionMessageFunctionToolCall(
                          id = "call_${UUID.randomUUID()}",
                          function = FunctionCall(
                            name = tc.name,
                            arguments = json.encodeToString(tc.arguments.mapValues { it.value.toJsonElement() })
                          )
                        )
                     }
                     val chunk = ChatCompletionChunk(
                       id = requestId,
                       created = created,
                       model = request.model,
                       choices = listOf(Choice(index = 0, delta = Delta(tool_calls = toolCallsDto), finish_reason = "tool_calls"))
                     )
                     write("data: ${json.encodeToString(chunk)}\n\n")
                     flush()
                  }

                  // Send finish reason
                  val doneChunk =
                    ChatCompletionChunk(
                      id = requestId,
                      created = created,
                      model = request.model,
                      choices = listOf(Choice(
                        index = 0,
                        delta = Delta(),
                        finish_reason = if (hasToolCalls) "tool_calls" else "stop")),
                    )
                  write("data: ${json.encodeToString(doneChunk)}\n\n")
                  write("data: [DONE]\n\n")
                  flush()
                }
              } else {
                inferenceMutex.withLock {
                  val fullResponse = CompletableDeferred<String>()
                  val toolCallsResult = mutableListOf<ChatCompletionMessageToolCall>()
                  val buffer = StringBuilder()

                  val initialMessages = buildInitialMessages(request.messages)
                  var contents = buildContents(request.messages)

                  LlmChatModelHelper.newConversation(
                    model = model,
                    supportImage = model.llmSupportImage,
                    supportAudio = model.llmSupportAudio,
                    systemInstruction = request.messages.firstOrNull { it.role == "system" }?.let { Contents.of(Content.Text(it.textContent)) },
                    initialMessages = initialMessages,
                    tools = tools,
                    enableConversationConstrainedDecoding = false,
                    temperature = request.temperature,
                    topP = request.top_p,
                    seed = request.seed,
                  )
                  delay(500)

                  ApiServerStatus.setInferring(true)
                  LlmChatModelHelper.runInference(
                    model = model,
                    input = userInput,
                    toolCallListener = { toolCalls ->
                       val mapped = toolCalls.map { tc ->
                         ChatCompletionMessageFunctionToolCall(
                           id = "call_${UUID.randomUUID()}",
                           function = FunctionCall(
                             name = tc.name,
                             arguments = json.encodeToString(tc.arguments.mapValues { it.value.toJsonElement() })
                           )
                         )
                       }
                       toolCallsResult.addAll(mapped)
                    },
                    resultListener = { partial, done, thinking ->
                      if (partial.startsWith("<ctrl")) {
                        // Do nothing. Ignore control tokens.
                      } else {
                        if (!thinking.isNullOrEmpty()) {
                          buffer.append(thinking)
                        }
                        buffer.append(partial)
                      }
                      if (done) {
                        ApiServerStatus.setInferring(false)
                        fullResponse.complete(buffer.toString())
                      }
                    },
                    cleanUpListener = { ApiServerStatus.setInferring(false) },
                    onError = { error ->
                      ApiServerStatus.setInferring(false)
                      fullResponse.completeExceptionally(Exception(error))
                    },
                    images = images,
                    audioClips = audioClips,
                    toolResponses = toolResponses,
                    extraContext = extraContext,
                  )

                  try {
                    val content = fullResponse.await()
                    call.respond(
                      ChatCompletionResponse(
                        id = requestId,
                        created = created,
                        model = request.model,
                        choices =
                          listOf(
                            Choice(
                              index = 0,
                              message =
                                ChatCompletionMessage(
                                  role = "assistant",
                                  content = if (content.isEmpty() && toolCallsResult.isNotEmpty()) null else content,
                                  tool_calls = if (toolCallsResult.isEmpty()) null else toolCallsResult
                                ),
                              finish_reason = if (toolCallsResult.isNotEmpty()) "tool_calls" else "stop",
                            )
                          ),
                      )
                    )
                  } catch (e: Exception) {
                    call.respond(
                      HttpStatusCode.InternalServerError,
                      mapOf("error" to mapOf("message" to (e.message ?: "Inference failed"))),
                    )
                  }
                }
              }
            } finally {
              ApiServerStatus.decrementRequestCount()
            }
          }
        }
      }.start(wait = false)
  }

  private fun buildContents(messages: List<ChatCompletionMessageParam>): MutableList<Content> {
    return mutableListOf(messages.last())
      .filter { it.role != "system" }
      .map {
        when (it.role) {
          "tool" -> {
            Content.ToolResponse("get_weather", it.textContent)
          }
          else -> Content.Text(it.textContent)
        }
      }
      .toMutableList()
  }

  private fun buildInitialMessages(messages: List<ChatCompletionMessageParam>): List<Message> {
    var history = messages.dropLast(1)
    return history
      .filter { it.role != "system" }
      .map {
        when (it.role) {
          "user" -> Message.user(it.textContent)
          "assistant" -> {
            val toolCalls = it.tool_calls?.map { tc ->
              when (tc) {
                is ChatCompletionMessageFunctionToolCall -> {
                  val element = json.decodeFromString<JsonElement>(tc.function.arguments)
                  ToolCall(
                    name = tc.function.name,
                    arguments = element.toAny() as Map<String, Any?>
                  )
                }
              }
            } ?: emptyList()
            Message.model(contents = Contents.of(Content.Text(it.textContent)), toolCalls = toolCalls)
          }
          "tool" -> Message.tool(Contents.of(Content.Text(it.textContent)))
          else -> Message.user(it.textContent)
        }
      }
  }

  private suspend fun loadBitmap(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        if (url.startsWith("data:image")) {
          val base64Data = url.substringAfter("base64,")
          val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
          return@withContext BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } else {
          val connection = URL(url).openConnection()
          connection.connect()
          connection.getInputStream().use {
            return@withContext BitmapFactory.decodeStream(it)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading bitmap from $url", e)
        null
      }
    }

  private data class ResizeConfig(val maxLongEdge: Int, val maxPixels: Long)

  private fun getResizeConfig(detail: ImageDetail): ResizeConfig {
    return when (detail) {
      ImageDetail.LOW -> ResizeConfig(maxLongEdge = 512, maxPixels = 512L * 512)
      ImageDetail.AUTO -> ResizeConfig(maxLongEdge = 1024, maxPixels = 1024L * 1024)
      ImageDetail.HIGH -> ResizeConfig(maxLongEdge = Int.MAX_VALUE, maxPixels = Long.MAX_VALUE)
    }
  }

  private fun resizeIfNeeded(bitmap: Bitmap, config: ResizeConfig): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    val longEdge = maxOf(width, height)
    val pixels = width.toLong() * height

    var scale = 1.0f

    if (longEdge > config.maxLongEdge) {
      scale = minOf(scale, config.maxLongEdge.toFloat() / longEdge)
    }

    if (pixels > config.maxPixels) {
      val pixelScale = sqrt(config.maxPixels.toFloat() / pixels)
      scale = minOf(scale, pixelScale)
    }

    if (scale >= 1.0f) return bitmap

    val newW = (width * scale).toInt()
    val newH = (height * scale).toInt()

    return bitmap.scale(newW, newH)
  }

  private fun decodeToMonoPcm(audioBytes: ByteArray, maxSeconds: Int = 30): ByteArray? {
    try {
      val extractor = MediaExtractor()
      val dataSource = object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
          if (position >= audioBytes.size) return -1
          val bytesToRead = minOf(size.toLong(), audioBytes.size - position).toInt()
          System.arraycopy(audioBytes, position.toInt(), buffer, offset, bytesToRead)
          return bytesToRead
        }
        override fun getSize(): Long = audioBytes.size.toLong()
        override fun close() {}
      }
      extractor.setDataSource(dataSource)

      val trackIndex = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: return null

      val format = extractor.getTrackFormat(trackIndex)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
      val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val originalChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

      val codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()
      extractor.selectTrack(trackIndex)

      val pcmData = mutableListOf<Short>()
      val info = MediaCodec.BufferInfo()
      var isExtractorDone = false
      var isDecoderDone = false

      while (!isDecoderDone) {
        if (!isExtractorDone) {
          val inputBufferIndex = codec.dequeueInputBuffer(10000)
          if (inputBufferIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
              codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              isExtractorDone = true
            } else {
              codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
        if (outputBufferIndex >= 0) {
          val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
          val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
          while (shortBuffer.hasRemaining()) {
            pcmData.add(shortBuffer.get())
          }
          codec.releaseOutputBuffer(outputBufferIndex, false)
          if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) isDecoderDone = true
        }
        if (pcmData.size / originalChannels > maxSeconds * originalSampleRate) isDecoderDone = true
      }

      codec.stop()
      codec.release()
      extractor.release()

      var pcmSamples = pcmData.toShortArray()
      var sampleRate = originalSampleRate

      if (sampleRate != SAMPLE_RATE && sampleRate > 0) {
        pcmSamples = resample(pcmSamples, sampleRate, SAMPLE_RATE, originalChannels)
        sampleRate = SAMPLE_RATE
      }

      var monoSamples = if (originalChannels >= 2) {
        val mono = ShortArray(pcmSamples.size / originalChannels)
        for (i in mono.indices) {
          var sum = 0
          for (c in 0 until originalChannels) sum += pcmSamples[i * originalChannels + c]
          mono[i] = (sum / originalChannels).toShort()
        }
        mono
      } else {
        pcmSamples
      }

      val maxSamples = maxSeconds * sampleRate
      if (monoSamples.size > maxSamples) monoSamples = monoSamples.copyOfRange(0, maxSamples)

      val monoByteBuffer = ByteBuffer.allocate(monoSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
      monoByteBuffer.asShortBuffer().put(monoSamples)
      return monoByteBuffer.array()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode audio", e)
      return null
    }
  }

  private fun resample(inputSamples: ShortArray, originalSampleRate: Int, targetSampleRate: Int, channels: Int): ShortArray {
    if (originalSampleRate == targetSampleRate || originalSampleRate <= 0) return inputSamples
    val ratio = targetSampleRate.toDouble() / originalSampleRate
    val inputFrames = inputSamples.size / channels
    val outputFrames = (inputFrames * ratio).toInt()
    val resampledData = ShortArray(outputFrames * channels)
    for (f in 0 until outputFrames) {
      val position = f / ratio
      val index1 = floor(position).toInt()
      val index2 = minOf(index1 + 1, inputFrames - 1)
      val fraction = position - index1
      for (c in 0 until channels) {
        val sample1 = inputSamples[index1 * channels + c].toDouble()
        val sample2 = inputSamples[index2 * channels + c].toDouble()
        resampledData[f * channels + c] = (sample1 * (1 - fraction) + sample2 * fraction).toInt().toShort()
      }
    }
    return resampledData
  }

  private fun wrapPcmInWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
    val header = ByteArray(44)
    val pcmDataSize = pcmData.size
    val wavFileSize = pcmDataSize + 44
    val channels = 1
    val bitsPerSample: Short = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8

    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray())
    buffer.putInt(wavFileSize - 8)
    buffer.put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16)
    buffer.putShort(1) // PCM
    buffer.putShort(channels.toShort())
    buffer.putInt(sampleRate)
    buffer.putInt(byteRate)
    buffer.putShort((channels * bitsPerSample / 8).toShort())
    buffer.putShort(bitsPerSample)
    buffer.put("data".toByteArray())
    buffer.putInt(pcmDataSize)

    return header + pcmData
  }

  fun stop() {
    server?.stop(1000, 3000)
    server = null
    ApiServerStatus.reset()
  }

  private fun Any?.toJsonElement(): JsonElement {
    return when (this) {
      null -> JsonNull
      is Number -> JsonPrimitive(this)
      is Boolean -> JsonPrimitive(this)
      is String -> JsonPrimitive(this)
      is Map<*, *> -> {
        val jsonObject = mutableMapOf<String, JsonElement>()
        for ((key, value) in this) {
          jsonObject[key.toString()] = value.toJsonElement()
        }
        JsonObject(jsonObject)
      }
      is Iterable<*> -> {
        val jsonArray = mutableListOf<JsonElement>()
        for (value in this) {
          jsonArray.add(value.toJsonElement())
        }
        JsonArray(jsonArray)
      }
      else -> JsonPrimitive(this.toString())
    }
  }
}

fun JsonElement.toAny(): Any? {
  return when (this) {
    is JsonNull -> null
    is JsonPrimitive -> {
      when {
        isString -> content
        content.equals("true", true) -> true
        content.equals("false", true) -> false
        content.contains('.') -> content.toDoubleOrNull()
        else -> content.toIntOrNull() ?: content
      }
    }
    is JsonObject -> mapValues { it.value.toAny() }
    is JsonArray -> map { it.toAny() }
  }
}
