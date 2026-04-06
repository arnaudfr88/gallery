package com.google.ai.edge.gallery.ui.apiserver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiServerScreen(
  viewModel: ApiServerViewModel = hiltViewModel(),
  onBackClicked: () -> Unit,
) {
  val isRunning by viewModel.isRunning.collectAsState()
  val port by viewModel.port.collectAsState()
  val localIp = remember { getLocalIpAddress() }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("OpenAI API Server") },
        navigationIcon = {
          IconButton(onClick = onBackClicked) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .padding(16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Text("Status: ${if (isRunning) "Running" else "Stopped"}", 
           color = if (isRunning) Color.Green else Color.Red,
           style = MaterialTheme.typography.titleMedium)
      
      if (isRunning) {
        Text("Endpoint: http://$localIp:$port/v1")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "Example Usage:",
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          "curl http://$localIp:$port/v1/chat/completions \\\n" +
          "  -H 'Content-Type: application/json' \\\n" +
          "  -d '{\"model\":\"<model_name>\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}'",
          style = MaterialTheme.typography.bodySmall
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { if (isRunning) viewModel.stopServer() else viewModel.startServer() }
      ) {
        Text(if (isRunning) "Stop Server" else "Start Server")
      }
    }
  }
}

fun getLocalIpAddress(): String {
  try {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    for (intf in interfaces) {
      val addrs = intf.inetAddresses
      for (addr in addrs) {
        if (!addr.isLoopbackAddress && addr is Inet4Address) {
          return addr.hostAddress ?: "unknown"
        }
      }
    }
  } catch (ex: Exception) {
    ex.printStackTrace()
  }
  return "unknown"
}
