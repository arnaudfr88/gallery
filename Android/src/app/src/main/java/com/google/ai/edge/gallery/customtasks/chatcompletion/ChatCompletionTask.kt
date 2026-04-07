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

package com.google.ai.edge.gallery.customtasks.chatcompletion

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.CategoryInfo
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * A custom task that displays the status of the OpenAI API Server and allows starting/stopping it.
 */
class ChatCompletionTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = "chat_completion_server_task",
      label = "Chat Completion",
      category = CategoryInfo(id = "server", label = "Server"),
      icon = Icons.Outlined.SwapVert,
      description = "Manage the local OpenAI API Server for chat completions.",
      models = mutableListOf(), // This task might not need specific models if it just manages the server,
      modelNames = mutableListOf(
        "Gemma-4-E2B-it",
        "Gemma-4-E4B-it",
        "Gemma-3n-E2B-it",
        "Gemma-3n-E4B-it",
        "Gemma3-1B-IT",
        "Qwen2.5-1.5B-Instruct",
        "DeepSeek-R1-Distill-Qwen-1.5B"),
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      coroutineScope = coroutineScope,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskData
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    ChatCompletionTaskScreen(
      modelManagerViewModel = myData.modelManagerViewModel,
      navigateUp = {
        myData.setCustomNavigateUpCallback(null)
        onBackPressedDispatcher?.onBackPressed()
      },
      setCustomNavigateUpCallback = myData.setCustomNavigateUpCallback
    )
  }
}
