package com.temanqris.listener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service yang fungsinya hanya untuk:
 *  1. Menampilkan notifikasi persistent supaya OS tidak agresif kill app
 *  2. Memberitahu user bahwa listener sedang aktif
 *
 * Service ini TIDAK melakukan listening notifikasi (itu tugas
 * PaymentNotificationListener). Tapi keberadaannya membantu listener
 * tetap aktif di background.
 */
class ListenerKeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "temanqris_listener_status"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ListenerKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenerKeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY  // restart kalau di-kill OS
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TemanQRIS Listener Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status listener pembayaran"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TemanQRIS Listener Aktif")
            .setContentText("Memantau notifikasi pembayaran masuk")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
