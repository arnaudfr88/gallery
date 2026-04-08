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

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.getLocalIpAddresses
import com.google.ai.edge.gallery.ui.apiserver.ApiServerViewModel
import com.google.ai.edge.gallery.ui.common.MarkdownText
import com.google.ai.edge.gallery.ui.common.RotationalLoader
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import androidx.core.net.toUri

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
  val host by apiServerViewModel.host.collectAsState()
  val isInferring by apiServerViewModel.isInferring.collectAsState()
  val isRequesting by apiServerViewModel.isRequesting.collectAsState(initial = false)
  val requestCount by apiServerViewModel.requestCount.collectAsState()

  var showConfirmDialog by remember { mutableStateOf(false) }
  var showIpDialog by remember { mutableStateOf(false) }

  val isModelInitialized = modelManagerUiState.isModelInitialized(model = model)
  LaunchedEffect(isModelInitialized) {
    apiServerViewModel.setPort(modelManagerViewModel.dataStoreRepository.readServerPort())
    apiServerViewModel.setHost(modelManagerViewModel.dataStoreRepository.readServerHost())
    if (isModelInitialized && !isRunning) {
      if (modelManagerViewModel.dataStoreRepository.readAutoStartServer()) {
        apiServerViewModel.startServer()
      }
    }
  }

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

  if (showIpDialog) {
    val context = LocalContext.current
    val ipAddresses = if (host == "0.0.0.0") {
      getLocalIpAddresses().ifEmpty { listOf("127.0.0.1") }
    } else {
      listOf(host)
    }

    AlertDialog(
      onDismissRequest = { showIpDialog = false },
      title = { Text(stringResource(R.string.server_cc_ip_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          ipAddresses.forEach { host ->
            val url = "http://$host:$port/"
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                  context.startActivity(intent)
                }
                .padding(vertical = 4.dp)
            ) {
              Text(
                text = url,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
              )
              Text(
                text = stringResource(R.string.server_cc_ip_open_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showIpDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      }
    )
  }

  if (modelManagerUiState.isModelInitialized(model = model)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top
    ) {
      Text(
        text = stringResource(R.string.server_cc_status_title),
        style =
          MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Medium,
            brush =
              Brush.linearGradient(colors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1))),
          ),
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        if (isRunning) {
          val infiniteTransition = rememberInfiniteTransition(label = "request_rotation")
          val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
              animation = tween(2000, easing = LinearEasing),
              repeatMode = RepeatMode.Restart
            ),
            label = "request_rotation"
          )

          val requestingBrush = remember {
            Brush.linearGradient(colors = listOf(Color(0xFFFFD54F), Color(0xFFF57C00)))
          }
          val idleBrush = remember {
            Brush.linearGradient(colors = listOf(Color(0xFF81C784), Color(0xFF388E3C)))
          }
          val currentBrush = if (isRequesting) requestingBrush else idleBrush

          if (requestCount > 1) {
            Text(
              text = requestCount.toString(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFFF57C00),
              modifier = Modifier.padding(end = 4.dp)
            )
          }

          Icon(
            imageVector = if (isRequesting) Icons.Filled.AllInclusive else Icons.Filled.AlternateEmail,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier
              .size(24.dp)
              .then(if (isRequesting) Modifier.rotate(rotationAngle) else Modifier)
              .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
              .drawWithCache {
                onDrawWithContent {
                  drawContent()
                  drawRect(currentBrush, blendMode = BlendMode.SrcIn)
                }
              }
          )
          Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
          text = if (isRunning) stringResource(R.string.server_cc_status_running, port)
            else stringResource(R.string.server_cc_status_stopped),
          color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyLarge,
          modifier = if (isRunning) {
            Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { showIpDialog = true }
              .padding(horizontal = 4.dp)
          } else {
            Modifier
          }
        )
      }

      if (isRunning) {
        Spacer(modifier = Modifier.height(12.dp))
        val inferenceStatusText = if (isInferring) {
          stringResource(R.string.server_cc_inference_ing)
        } else {
          stringResource(R.string.server_cc_inference_idle)
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          if (isInferring) {
            RotationalLoader(size = 24.dp)
          } else {
            StaticRotationalLoader(size = 24.dp)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.server_cc_inference_status, inferenceStatusText),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isInferring) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

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

      if (isRunning) {
        Spacer(modifier = Modifier.height(12.dp))
        ApiEndpointCard(
          title = "/",
          assetPath = "api/root.md"
        )
        Spacer(modifier = Modifier.height(12.dp))
        ApiEndpointCard(
          title = "/v1/models",
          assetPath = "api/v1_models.md"
        )
        Spacer(modifier = Modifier.height(12.dp))
        ApiEndpointCard(
          title = "/v1/chat/completions",
          assetPath = "api/v1_chat_completions.md"
        )
      }
    }
  } else {
    val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[model.name]
    val isInitializing =
      modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      AnimatedVisibility(
        visible = isInitializing,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          RotationalLoader(size = 32.dp)
          Text(
            stringResource(R.string.aichat_initializing_title),
            style =
              MaterialTheme.typography.headlineLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
              ),
          )
          Text(
            stringResource(R.string.aichat_initializing_content),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
private fun StaticRotationalLoader(size: Dp) {
  val gridSpacing = size * 0.1f
  val iconSizeFactor = 0.3f
  val curRotationZ = 45f
  val curScale = 1f

  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
    verticalArrangement = Arrangement.spacedBy(gridSpacing),
    modifier = Modifier.size(size).graphicsLayer { rotationZ = curRotationZ },
    userScrollEnabled = false
  ) {
    itemsIndexed(
      listOf(
        R.drawable.four_circle,
        R.drawable.circle,
        R.drawable.double_circle,
        R.drawable.pantegon,
      )
    ) { index, imageResource ->
      Box(
        modifier = Modifier.size((size - gridSpacing) / 2),
        contentAlignment =
          when (index) {
            0 -> Alignment.BottomEnd
            1 -> Alignment.BottomStart
            2 -> Alignment.TopEnd
            3 -> Alignment.TopStart
            else -> Alignment.Center
          },
      ) {
        val colorIndex =
          when (index) {
            0 -> 2
            1 -> 1
            2 -> 0
            else -> 3
          }
        val brush =
          Brush.linearGradient(colors = MaterialTheme.customColors.taskBgGradientColors[colorIndex])
        Image(
          painter = painterResource(id = imageResource),
          contentDescription = null,
          modifier =
            Modifier.size(size * iconSizeFactor)
              .graphicsLayer {
                alpha = 0.99f
                rotationZ = -curRotationZ
                scaleX = curScale
                scaleY = curScale
              }
              .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
              },
          contentScale = ContentScale.Fit,
        )
      }
    }
  }
}

@Composable
fun ApiEndpointCard(title: String, assetPath: String) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val content by produceState(initialValue = "") {
    value = try {
      context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
      ""
    }
  }

  var expanded by remember { mutableStateOf(false) }
  var columnHeightDp by remember { mutableStateOf(0.dp) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(brush = MaterialTheme.customColors.promoBannerBgBrush)
      .clickable { expanded = !expanded }
      .animateContentSize()
  ) {
    // Decoration Icon
    val iconBrush = MaterialTheme.customColors.promoBannerIconBgBrush
    Image(
      ImageVector.vectorResource(R.drawable.gemini_star),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier =
        Modifier.height(columnHeightDp)
          .width(columnHeightDp)
          .align(Alignment.CenterEnd)
          .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen, alpha = 0.6f)
          .drawWithContent {
            drawContent()
            drawRect(brush = iconBrush, blendMode = BlendMode.SrcIn)
          },
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coordinates ->
          columnHeightDp = with(density) { coordinates.size.height.toDp() }
        }
        .padding(16.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(8.dp))

      val containerModifier = if (!expanded) {
        Modifier
          .heightIn(max = 100.dp)
          .clipToBounds() // Ensure markers from hidden content are clipped
          .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
          .drawWithCache {
            val gradient = Brush.verticalGradient(
              colors = listOf(Color.Black, Color.Transparent),
              startY = size.height * 0.7f,
              endY = size.height
            )
            onDrawWithContent {
              drawContent()
              drawRect(gradient, blendMode = BlendMode.DstIn)
            }
          }
      } else {
        Modifier
      }

      Box(modifier = containerModifier) {
        MarkdownText(
          text = content,
          smallFontSize = true,
          modifier = Modifier
            .fillMaxWidth()
            // Important: measured with unbounded height when folded to prevent
            // the layout engine from squashing list items and overlapping markers.
            .then(if (!expanded) Modifier.wrapContentHeight(unbounded = true, align = Alignment.Top) else Modifier)
        )
      }
    }
  }
}
