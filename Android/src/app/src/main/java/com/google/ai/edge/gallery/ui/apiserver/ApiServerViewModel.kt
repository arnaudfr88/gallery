package com.google.ai.edge.gallery.ui.apiserver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.service.ApiServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ApiServerViewModel @Inject constructor(
  @ApplicationContext private val context: Context
) : ViewModel() {

  private val _isRunning = MutableStateFlow(false)
  val isRunning = _isRunning.asStateFlow()

  private val _port = MutableStateFlow(8080)
  val port = _port.asStateFlow()

  val isInferring = ApiServerStatus.isInferring
  val requestCount = ApiServerStatus.requestCount
  val isRequesting = requestCount.map { it > 0 }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun startServer() {
    ApiServerService.start(context, _port.value)
    _isRunning.value = true
  }

  fun stopServer() {
    ApiServerService.stop(context)
    _isRunning.value = false
  }

  fun setPort(port: Int) {
    _port.value = port
  }
}
