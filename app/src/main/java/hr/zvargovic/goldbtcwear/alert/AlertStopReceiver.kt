package hr.zvargovic.goldbtcwear.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class AlertStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertAlarm.stop(context)
        // Zatvori notifikaciju
        NotificationManagerCompat.from(context).cancel(2001)
    }
}