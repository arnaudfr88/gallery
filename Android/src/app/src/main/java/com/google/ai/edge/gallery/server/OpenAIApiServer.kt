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

import com.google.ai.edge.gallery.server.dto.ChatCompletionChunk
import com.google.ai.edge.gallery.server.dto.ChatCompletionRequest
import com.google.ai.edge.gallery.server.dto.ChatCompletionResponse
import com.google.ai.edge.gallery.server.dto.ChatMessage
import com.google.ai.edge.gallery.server.dto.Choice
import com.google.ai.edge.gallery.server.dto.Delta
import com.google.ai.edge.gallery.server.dto.ModelListResponse
import com.google.ai.edge.gallery.server.dto.ModelResponse
import com.google.ai.edge.gallery.service.ModelManagerAccessor
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
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
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class OpenAIApiServer(
    private val modelManager: ModelManagerAccessor,
    private val port: Int = 8080,
) {
  private var server: ApplicationEngine? = null
  private val json = Json { ignoreUnknownKeys = true }

  fun start() {
    server = embeddedServer(CIO, port = port) {
      install(ContentNegotiation) {
        json(json)
      }

      routing {
        // Health check
        get("/") {
          call.respondText("AI Edge Gallery OpenAI API Server")
        }

        // List models
        get("/v1/models") {
          val models = modelManager.getDownloadedModelNames()
            .map { modelName ->
              ModelResponse(
                id = modelName,
                created = System.currentTimeMillis() / 1000
              )
            }
          call.respond(ModelListResponse(data = models))
        }

        // Chat completions
        post("/v1/chat/completions") {
          val request = call.receive<ChatCompletionRequest>()

          // Find the corresponding downloaded model
          val model = modelManager.getModelByName(request.model)
          if (model == null || model.instance == null) {
            call.respond(
              HttpStatusCode.NotFound,
              mapOf("error" to mapOf("message" to "Model '${request.model}' not found or not initialized"))
            )
            return@post
          }

          val userInput = request.messages.lastOrNull { it.role == "user" }?.content ?: ""
          val requestId = "chatcmpl-${UUID.randomUUID()}"
          val created = System.currentTimeMillis() / 1000

          if (request.stream) {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
              val channel = Channel<String>(Channel.BUFFERED)

              CoroutineScope(Dispatchers.Default).launch {
                val history = request.messages.dropLast(1)
                val systemMessage = request.messages.firstOrNull { it.role == "system" }
                val initialMessages = history
                  .filter { it.role != "system" }
                  .map {
                    when (it.role) {
                      "user" -> Message.user(it.content)
                      "assistant" -> Message.model(it.content)
                      else -> Message.user(it.content)
                    }
                  }

                LlmChatModelHelper.newConversation(
                  model = model,
                  supportImage = model.llmSupportImage,
                  supportAudio = model.llmSupportAudio,
                  systemInstruction = systemMessage?.let { Contents.of(Content.Text(it.content)) },
                  initialMessages = initialMessages,
                  tools = listOf(),
                  enableConversationConstrainedDecoding = false
                )
                delay(500)

                LlmChatModelHelper.runInference(
                  model = model,
                  input = userInput,
                  resultListener = { partial, done, _ ->
                    if (partial.isNotEmpty()) {
                      channel.trySend(partial)
                    }
                    if (done) {
                      channel.close()
                    }
                  },
                  cleanUpListener = { channel.close() },
                  onError = { error ->
                    channel.close(Exception(error))
                  },
                )
              }

              // Send role delta
              val roleChunk = ChatCompletionChunk(
                id = requestId,
                created = created,
                model = request.model,
                choices = listOf(Choice(index = 0, delta = Delta(role = "assistant")))
              )
              write("data: ${json.encodeToString(roleChunk)}\n\n")
              flush()

              // Send tokens
              for (token in channel) {
                val chunk = ChatCompletionChunk(
                  id = requestId,
                  created = created,
                  model = request.model,
                  choices = listOf(Choice(index = 0, delta = Delta(content = token)))
                )
                write("data: ${json.encodeToString(chunk)}\n\n")
                flush()
              }

              // Send finish reason
              val doneChunk = ChatCompletionChunk(
                id = requestId,
                created = created,
                model = request.model,
                choices = listOf(Choice(index = 0, delta = Delta(), finish_reason = "stop"))
              )
              write("data: ${json.encodeToString(doneChunk)}\n\n")
              write("data: [DONE]\n\n")
              flush()
            }
          } else {
            val fullResponse = CompletableDeferred<String>()
            val buffer = StringBuilder()

            val history = request.messages.dropLast(1)
            val systemMessage = request.messages.firstOrNull { it.role == "system" }
            val initialMessages = history
              .filter { it.role != "system" }
              .map {
                when (it.role) {
                  "user" -> Message.user(it.content)
                  "assistant" -> Message.model(it.content)
                  else -> Message.user(it.content)
                }
              }

            LlmChatModelHelper.newConversation(
              model = model,
              supportImage = model.llmSupportImage,
              supportAudio = model.llmSupportAudio,
              systemInstruction = systemMessage?.let { Contents.of(Content.Text(it.content)) },
              initialMessages = initialMessages,
              tools = listOf(),
              enableConversationConstrainedDecoding = false
            )
            delay(500)

            LlmChatModelHelper.runInference(
              model = model,
              input = userInput,
              resultListener = { partial, done, _ ->
                buffer.append(partial)
                if (done) fullResponse.complete(buffer.toString())
              },
              cleanUpListener = {},
              onError = { error -> fullResponse.completeExceptionally(Exception(error)) },
            )

            try {
              val content = fullResponse.await()
              call.respond(
                ChatCompletionResponse(
                  id = requestId,
                  created = created,
                  model = request.model,
                  choices = listOf(
                    Choice(
                      index = 0,
                      message = ChatMessage(role = "assistant", content = content),
                      finish_reason = "stop",
                    )
                  ),
                )
              )
            } catch (e: Exception) {
               call.respond(
                 HttpStatusCode.InternalServerError,
                 mapOf("error" to mapOf("message" to (e.message ?: "Inference failed")))
               )
            }
          }
        }
      }
    }.start(wait = false)
  }

  fun stop() {
    server?.stop(1000, 3000)
    server = null
  }
}
