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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.apiserver.ApiServerViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun ChatCompletionTaskScreen(
  modelManagerViewModel: ModelManagerViewModel,
  apiServerViewModel: ApiServerViewModel = hiltViewModel(),
  navigateUp: () -> Unit,
  setCustomNavigateUpCallback: ((() -> Unit)?) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val isRunning by apiServerViewModel.isRunning.collectAsState()
  val port by apiServerViewModel.port.collectAsState()

  var showConfirmDialog by remember { mutableStateOf(false) }

  val handleNavigateUp = {
    val modelInitializationStatus =
      modelManagerUiState.modelInitializationStatus[model.name]
    val isModelInitializing =
      modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
    if (!isModelInitializing && !isRunning) {
      apiServerViewModel.stopServer()
      navigateUp()
    } else {
      showConfirmDialog = true
    }
  }

  LaunchedEffect(isRunning) {
    setCustomNavigateUpCallback {
      handleNavigateUp()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      setCustomNavigateUpCallback(null)
    }
  }

  if (showConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDialog = false },
      title = { Text(stringResource(R.string.server_cc_back_confirm_title)) },
      text = { Text(stringResource(R.string.server_cc_back_confirm_text)) },
      confirmButton = {
        TextButton(
          onClick = {
            showConfirmDialog = false
            apiServerViewModel.stopServer()
            navigateUp()
          }
        ) {
          Text(stringResource(R.string.ok))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showConfirmDialog = false
          }
        ) {
          Text(stringResource(R.string.cancel))
        }
      }
    )
  }

  if (modelManagerUiState.isModelInitialized(model = model)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = stringResource(R.string.server_cc_status_title),
        style = MaterialTheme.typography.headlineMedium
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = if (isRunning) stringResource(R.string.server_cc_status_running, port)
          else stringResource(R.string.server_cc_status_stopped),
        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge
      )
      Spacer(modifier = Modifier.height(24.dp))
      Button(
        onClick = {
          if (isRunning) {
            apiServerViewModel.stopServer()
          } else {
            apiServerViewModel.startServer()
          }
        }
      ) {
        Text(text = if (isRunning) stringResource(R.string.server_cc_btn_stop)
          else stringResource(R.string.server_cc_btn_start))
      }
    }
  }
  // Loading spinner.
  else {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeWidth = 3.dp,
      )
    }
  }
}
