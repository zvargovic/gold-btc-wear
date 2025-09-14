package hr.zvargovic.goldbtcwear.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hr.zvargovic.goldbtcwear.alarm.AlarmService

/**
 * Prima akciju hr.zvargovic.goldbtcwear.ALERT_STOP i gasi AlarmService.
 */
class AlertStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmService.ACTION_STOP) {
            AlarmService.stop(context)
        }
    }
}