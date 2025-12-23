package com.ai.assistance.shower

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shower Server 前台服务
 * 保持 WebSocket 服务运行
 */
class ShowerService : Service() {
    companion object {
        private const val TAG = "ShowerService"
        private const val CHANNEL_ID = "ShowerServerChannel"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isServerRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ShowerService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ShowerService started")

        if (!isServerRunning) {
            serviceScope.launch {
                try {
                    ShowerServer.start(applicationContext)
                    isServerRunning = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ShowerServer", e)
                }
            }
        }

        // START_STICKY: 服务被杀死后自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ShowerService destroyed")
        ShowerServer.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shower Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "虚拟屏幕服务运行中"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Shower Server")
                .setContentText("虚拟屏幕服务运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Shower Server")
                .setContentText("虚拟屏幕服务运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }
}
