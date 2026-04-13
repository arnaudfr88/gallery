package com.google.ai.edge.gallery.server

import android.util.Log
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider

private const val TAG = "AGLlmChatModelHelper"

@OptIn(ExperimentalApi::class)
fun LlmChatModelHelper.newConversation(
  model: Model,
  supportImage: Boolean,
  supportAudio: Boolean,
  systemInstruction: Contents?,
  initialMessages: List<Message>,
  tools: List<ToolProvider>,
  enableConversationConstrainedDecoding: Boolean,
  temperature: Float? = null,
  topP: Float? = null,
  seed: Int? = null,
) {
  try {
    Log.d(TAG, "Renew conversation for model '${model.name}'")

    val instance = model.instance as LlmModelInstance? ?: return
    instance.conversation.close()

    val engine = instance.engine
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val finalTopP = topP ?: model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val finalTemperature =
      temperature ?: model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val shouldEnableImage = supportImage
    val shouldEnableAudio = supportAudio
    Log.d(TAG, "Enable image: $shouldEnableImage, enable audio: $shouldEnableAudio")

    val accelerator =
      model.getStringConfigValue(
        key = ConfigKeys.ACCELERATOR,
        defaultValue = Accelerator.GPU.label,
      )
    ExperimentalFlags.enableConversationConstrainedDecoding =
      enableConversationConstrainedDecoding
    val newConversation =
      engine.createConversation(
        ConversationConfig(
          samplerConfig =
            if (accelerator == Accelerator.NPU.label) {
              null
            } else {
              SamplerConfig(
                topK = topK,
                topP = finalTopP.toDouble(),
                temperature = finalTemperature.toDouble(),
                seed = seed ?: 0,
              )
            },
          systemInstruction = systemInstruction,
          initialMessages = initialMessages,
          tools = tools,
          automaticToolCalling = false,
        )
      )
    ExperimentalFlags.enableConversationConstrainedDecoding = false
    instance.conversation = newConversation

    Log.d(TAG, "Resetting done")
  } catch (e: Exception) {
    Log.d(TAG, "Failed to reset conversation", e)
  }
}
