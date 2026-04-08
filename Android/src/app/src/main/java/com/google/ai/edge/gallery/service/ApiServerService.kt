package com.google.ai.edge.gallery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.server.OpenAIApiServer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ApiServerService : Service() {

  private var apiServer: OpenAIApiServer? = null

  @Inject
  lateinit var modelManagerHolder: ModelManagerHolder

  override fun onCreate() {
    super.onCreate()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val port = intent?.getIntExtra("port", 8080) ?: 8080
    val host = intent?.getStringExtra("host") ?: "0.0.0.0"

    if (apiServer == null) {
      val manager = modelManagerHolder.manager
      if (manager != null) {
        apiServer = OpenAIApiServer(
          context = this,
          modelManager = manager,
          port = port,
          host = host,
        )
        apiServer?.start()
      }
    }

    startForeground(NOTIFICATION_ID, buildNotification())
    return START_STICKY
  }

  override fun onDestroy() {
    apiServer?.stop()
    apiServer = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun buildNotification(): Notification {
    val channelId = "api_server"
    val channelName = "API Server"
    val notificationManager = getSystemService(NotificationManager::class.java)

    val channel = NotificationChannel(
      channelId,
      channelName,
      NotificationManager.IMPORTANCE_LOW,
    )
    notificationManager.createNotificationChannel(channel)

    return NotificationCompat.Builder(this, channelId)
      .setContentTitle(getString(R.string.server_service_notification_title))
      .setContentText(getString(R.string.server_service_notification_text, apiServer?.port))
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val NOTIFICATION_ID = 1001

    fun start(context: Context, port: Int = 8080, host: String = "0.0.0.0") {
      val intent = Intent(context, ApiServerService::class.java)
        .putExtra("port", port)
        .putExtra("host", host)
      context.startForegroundService(intent)
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, ApiServerService::class.java))
    }
  }
}
