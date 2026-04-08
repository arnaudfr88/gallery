package com.google.ai.edge.gallery.ui.apiserver

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ApiServerStatus {
  private val _isInferring = MutableStateFlow(false)
  val isInferring = _isInferring.asStateFlow()

  private val _isRequesting = MutableStateFlow(false)
  val isRequesting = _isRequesting.asStateFlow()

  fun setInferring(inferring: Boolean) {
    _isInferring.value = inferring
  }

  fun setRequesting(requesting: Boolean) {
    _isRequesting.value = requesting
  }
}
