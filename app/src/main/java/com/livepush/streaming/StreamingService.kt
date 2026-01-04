package com.livepush.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.livepush.R
import com.livepush.app.MainActivity
import com.livepush.domain.model.StreamState
import com.livepush.domain.usecase.StreamManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StreamingService : Service() {

    companion object {
        const val CHANNEL_ID = "streaming_channel"
        const val NOTIFICATION_ID = 1
    }

    @Inject
    lateinit var streamManager: StreamManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(NotificationManager::class.java)
        observeStreamState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun observeStreamState() {
        serviceScope.launch {
            streamManager.streamState.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    private fun updateNotification(state: StreamState) {
        val notification = when (state) {
            is StreamState.Reconnecting -> {
                createNotification(
                    contentText = getString(
                        R.string.reconnecting
                    ) + " " + getString(
                        R.string.reconnect_attempt,
                        state.attempt,
                        state.maxAttempts
                    )
                )
            }
            is StreamState.Streaming -> {
                createNotification()
            }
            else -> {
                // For other states, keep the default notification
                createNotification()
            }
        }
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        contentText: String = getString(R.string.notification_text)
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_live)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
