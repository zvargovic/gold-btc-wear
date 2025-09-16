package hr.zvargovic.goldbtcwear.workers

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.zvargovic.goldbtcwear.R
import hr.zvargovic.goldbtcwear.alarm.AlarmService
import hr.zvargovic.goldbtcwear.data.CorrectionStore
import hr.zvargovic.goldbtcwear.data.SelectedAlertStore
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.SpotStore
import hr.zvargovic.goldbtcwear.data.api.YahooService
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// ★ Novo: finalna korekcija (fiksni + %) na istoj finalnoj cijeni kao UI
import hr.zvargovic.goldbtcwear.domain.usecase.PriceAdjuster

private const val UNIQUE_PERIODIC_NAME = "alert-worker-periodic"

class AlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val ctx = applicationContext
    private val yahoo = YahooService()

    private val selectedStore = SelectedAlertStore(ctx)
    private val spotStore = SpotStore(ctx)
    private val corrStore = CorrectionStore(ctx)
    private val settingsStore = SettingsStore(ctx)

    override suspend fun doWork(): Result {
        // 1) Selektirani alert
        val selected = selectedStore.load() ?: return Result.success()

        // 2) Spot (legacy K iz TD) → FINAL (hardkod korekcija)
        val corrPct = try { corrStore.corrFlow.firstOrNull() ?: 0.0 } catch (_: Throwable) { 0.0 }
        val res = yahoo.getSpotEur()
        res.onFailure { return Result.retry() }

        val raw = res.getOrNull() ?: return Result.retry()
        val withK = raw * (1.0 + corrPct)
        val shown = PriceAdjuster.apply(withK) // FINAL kao na glavnom ekranu

        // 3) Pogodak (tolerancija) NAD FINALNOM CIJENOM
        val tolerance = 0.10
        val hit = abs(shown - selected) <= tolerance

        if (hit) {
            val alarmEnabled = try { settingsStore.alarmEnabledFlow.firstOrNull() ?: false } catch (_: Throwable) { false }
            if (alarmEnabled) {
                AlarmService.start(ctx)
            } else {
                postAlertNotification(selected, shown)
            }
            selectedStore.save(null)
        }

        // 4) Spremi LAST = FINAL i osvježi Tile
        spotStore.saveLast(shown)
        hr.zvargovic.goldbtcwear.tile.GoldTileService.requestUpdate(ctx)

        return Result.success()
    }

    /** Notifikacija bez pokretanja AlarmService-a (koristi kanal "alerts"). */
    private fun postAlertNotification(hitAt: Double, current: Double) {
        val title = ctx.getString(R.string.app_name)
        val text = ctx.getString(
            R.string.alert_hit_text,
            "€" + "%,.2f".format(hitAt),
            "€" + "%,.2f".format(current)
        )
        val notif = NotificationCompat.Builder(ctx, "alerts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(2002, notif)
    }

    companion object {
        private fun baseConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(baseConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun kickNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<AlertWorker>()
                .setConstraints(baseConstraints())
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}