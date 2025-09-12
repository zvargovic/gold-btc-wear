package hr.zvargovic.goldbtcwear.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.getSystemService
import hr.zvargovic.goldbtcwear.ui.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        setContent { AppNavHost() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "alerts"
            val name = "Price alerts"
            val desc = "Alert when target price is hit"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = desc
                enableVibration(true)
                setShowBadge(true)
            }
            val nm: NotificationManager? = getSystemService()
            nm?.createNotificationChannel(channel)
        }
    }
}