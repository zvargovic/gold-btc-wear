package hr.zvargovic.goldbtcwear.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object AlertAlarm {
    private const val TAG = "AlertAlarm"
    @Volatile private var player: MediaPlayer? = null

    fun start(context: Context) {
        vibrateLoop(context)
        playTone(context)
    }

    fun stop(context: Context) {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        try {
            val vib = vibrator(context)
            vib?.cancel()
        } catch (_: Throwable) {}
    }

    private fun playTone(context: Context) {
        if (player != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mp = MediaPlayer()
            mp.setDataSource(context, uri)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mp.setAudioAttributes(attrs)
            mp.isLooping = true
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            Log.w(TAG, "playTone failed: ${t.message}")
        }
    }

    private fun vibrateLoop(context: Context) {
        try {
            val vib = vibrator(context) ?: return
            val pattern = longArrayOf(
                0, 300, 150, 450, 180, 600, 250, 450   // petlja se jer repeat=0
            )
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION") vib.vibrate(pattern, 0)
            }
        } catch (_: Throwable) {}
    }

    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Throwable) { null }
}