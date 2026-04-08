package com.google.ai.edge.gallery.ui.apiserver

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ApiServerStatus {
  private val _isInferring = MutableStateFlow(false)
  val isInferring = _isInferring.asStateFlow()

  private val _requestCount = MutableStateFlow(0)
  val requestCount = _requestCount.asStateFlow()

  fun setInferring(inferring: Boolean) {
    _isInferring.value = inferring
  }

  fun incrementRequestCount() {
    _requestCount.update { it + 1 }
  }

  fun decrementRequestCount() {
    _requestCount.update { maxOf(0, it - 1) }
  }

  fun reset() {
    _isInferring.value = false
    _requestCount.value = 0
  }
}
