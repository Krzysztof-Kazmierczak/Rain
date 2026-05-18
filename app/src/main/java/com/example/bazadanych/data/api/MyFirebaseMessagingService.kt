package com.example.bazadanych.data.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.bazadanych.R
import com.example.bazadanych.ui.HomeActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Wywołuje się, gdy aplikacja jest otwarta, a powiadomienie przychodzi
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM_CLIENT", "Otrzymano wiadomość od: ${remoteMessage.from}")

        // Sprawdzamy czy powiadomienie zawiera treść
        remoteMessage.notification?.let {
            val title = it.title ?: "Status Deszczowni"
            val body = it.body ?: "Zmiana statusu"
            sendNotification(title, body)
        }
    }

    // Wywołuje się, gdy system wygeneruje nowy token (np. po czyszczeniu danych aplikacji)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_CLIENT", "Nowy token: $token")
        // Tutaj można by wysłać token na serwer, ale robisz to już w HomeActivity
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "deszczownie_status_channel"

        // Budowanie powiadomienia graficznego
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Możesz zmienić na własną ikonkę z R.drawable
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Kanały powiadomień są wymagane od Androida 8.0 (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Statusy Deszczowni",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}