package hr.zvargovic.goldbtcwear

import android.app.Application
import android.util.Log
import androidx.work.*
import hr.zvargovic.goldbtcwear.workers.TdCorrWorker
import java.util.concurrent.TimeUnit

class GoldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleTdCorrWorker(this)
    }

    companion object {
        // Konstante koje ti je javljalo kao "Unresolved reference"
        const val WORK_TAG = "TDWORK"
        const val UNIQUE_PERIODIC_NAME = "tdcorr_hourly"
        const val UNIQUE_BOOT_NAME = "tdcorr_boot"
    }
}

/**
 * Enqueue-a:
 *  - periodic (svakih 1h) za TdCorrWorker
 *  - jednokratni odmah po bootu app-a (da brzo osvježi K)
 */
fun scheduleTdCorrWorker(app: Application) {
    val wm = WorkManager.getInstance(app)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // 1) Periodic: svaka 1h
    val hourly = PeriodicWorkRequestBuilder<TdCorrWorker>(1, TimeUnit.HOURS)
        .addTag(GoldApp.WORK_TAG)
        .setConstraints(constraints)
        .build()

    wm.enqueueUniquePeriodicWork(
        GoldApp.UNIQUE_PERIODIC_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        hourly
    )

    // 2) Jednokratni odmah (po startu app-a)
    val bootOneShot = OneTimeWorkRequestBuilder<TdCorrWorker>()
        .addTag(GoldApp.WORK_TAG)
        .setConstraints(constraints)
        .build()

    wm.enqueueUniqueWork(
        GoldApp.UNIQUE_BOOT_NAME,
        ExistingWorkPolicy.KEEP,
        bootOneShot
    )

    Log.i(GoldApp.WORK_TAG, "scheduled periodic=${hourly.id} boot=${bootOneShot.id}")
}