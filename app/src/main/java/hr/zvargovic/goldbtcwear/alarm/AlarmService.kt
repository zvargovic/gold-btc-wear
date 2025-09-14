package hr.zvargovic.goldbtcwear.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hr.zvargovic.goldbtcwear.R

/**
 * Foreground servis: loop zvuk + kontinuirana vibracija dok korisnik ne pritisne STOP.
 * Notifikacija ima full-screen intent (digne STOP ekran) i STOP action.
 */
class AlarmService : Service() {

    private var mp: MediaPlayer? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Drži CPU budnim dok alarm traje
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GoldBtcWear:AlarmWakelock"
        ).apply { acquire(10 * 60 * 1000L) } // max 10 min safety

        // ---- MediaPlayer bez resursa (default alarm/notification zvuk) ----
        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            setDataSource(this@AlarmService, soundUri)
            // Ako je zvuk nedostupan, MediaPlayer baci iznimku — uhvati se kod starta
            prepare()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        // Zvuk + vibracija
        try { mp?.start() } catch (_: Throwable) {}
        vibrateLoop()

        // Fallback: odmah digni STOP ekran (nekad OS "proguta" fullScreenIntent)
        try {
            val stopUi = Intent(this, AlarmStopActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(stopUi)
        } catch (_: Throwable) {}

        return START_STICKY
    }

    override fun onDestroy() {
        stopVibration()
        try { mp?.stop() } catch (_: Throwable) {}
        try { mp?.release() } catch (_: Throwable) {}
        mp = null
        try { wl?.release() } catch (_: Throwable) {}
        wl = null
        super.onDestroy()
    }

    private fun vibrateLoop() {
        try {
            val pattern = longArrayOf(0, 600, 200, 800, 200, 800) // repeat
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(pattern, 0)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun stopVibration() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
            }
        } catch (_: Throwable) {}
    }

    private fun buildNotification(): Notification {
        val channelId = "alerts" // isti kanal koji već koristiš

        // STOP action (Broadcast → AlertStopReceiver → AlarmService.stop())
        val stopPending = PendingIntent.getBroadcast(
            this,
            100,
            Intent(ACTION_STOP).setPackage(packageName),
            (PendingIntent.FLAG_UPDATE_CURRENT
                    or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Full-screen STOP UI
        val fullScreen = PendingIntent.getActivity(
            this,
            101,
            Intent(this, AlarmStopActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            (PendingIntent.FLAG_UPDATE_CURRENT
                    or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = getString(R.string.app_name)
        val text  = getString(R.string.alarm_ringing) // strings.xml

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)                 // koristimo launcher ikonu
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, getString(R.string.stop), stopPending) // bez ic_stop drawable-a
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 7001
        const val ACTION_STOP = "hr.zvargovic.goldbtcwear.ALERT_STOP"

        fun start(context: Context) {
            val i = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            try { NotificationManagerCompat.from(context).cancel(NOTIF_ID) } catch (_: Throwable) {}
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}