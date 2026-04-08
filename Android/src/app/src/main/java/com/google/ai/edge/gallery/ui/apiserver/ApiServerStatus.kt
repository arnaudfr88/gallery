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

package com.google.ai.edge.gallery.ui.apiserver

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ApiServerStatus {
  private val _isInferring = MutableStateFlow(false)
  val isInferring = _isInferring.asStateFlow()

  private val _requestCount = MutableStateFlow(0)
  val requestCount = _requestCount.asStateFlow()

  private val externalScope = CoroutineScope(Dispatchers.IO)

  fun setInferring(inferring: Boolean) {
    _isInferring.value = inferring
  }

  fun incrementRequestCount() {
    _requestCount.update { it + 1 }
    externalScope.launch {
      try {
        val url = URL("https://api.counterapi.dev/v2/xiaoyao9184s-team-3648/gallery-as-server/up")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer ut_DummazCmTWyvfjyQBUofiB9BFazeKRRinlx0uIOA")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.inputStream.use { it.readBytes() }
      } catch (e: Exception) {
        // Ignore error
      }
    }
  }

  fun decrementRequestCount() {
    _requestCount.update { maxOf(0, it - 1) }
  }

  fun reset() {
    _isInferring.value = false
    _requestCount.value = 0
  }
}
