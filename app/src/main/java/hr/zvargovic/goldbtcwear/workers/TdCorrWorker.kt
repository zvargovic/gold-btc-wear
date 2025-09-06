package hr.zvargovic.goldbtcwear.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import hr.zvargovic.goldbtcwear.data.CorrectionStore // ili hr.zvargovic.goldbtcwear.data.CorrectionStore ako si tako nazvao datoteku
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.api.TwelveDataService
import hr.zvargovic.goldbtcwear.data.api.YahooService

/**
 * Svakih ~1h:
 *  - TD spot EUR (XAU/USD / EUR/USD)  => "realni" spot
 *  - Y! "spot" EUR (u praksi futures GC=F preračunat u EUR)
 *  - korekcija K = TD / Y!  - 1
 * UI onda prikazuje:  Y! * (1+K)
 */
class TdCorrWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx)
        val corrStore = CorrectionStore(ctx)

        val apiKey = settings.loadApiKey()
        if (apiKey.isBlank()) {
            // bez ključa ne možemo na TD → probaj kasnije
            return ListenableWorker.Result.retry()
        }

        val td = TwelveDataService()
        val yahoo = YahooService()

        // TD SPOT (EUR)
        val tdSpotEur = td.getSpotEur(apiKey).getOrNull()
        // Y! “spot” (EUR) — u tvojoj implementaciji ovo je GC=F (futures) preračunat u EUR
        val yFutEur = yahoo.getSpotEur().getOrNull()

        if (tdSpotEur == null || yFutEur == null || tdSpotEur <= 0.0 || yFutEur <= 0.0) {
            return ListenableWorker.Result.retry()
        }

        // K = TD / Y! - 1  (npr. +0.0042 = +0.42 %)
        val k = (tdSpotEur / yFutEur) - 1.0
        corrStore.save(k, System.currentTimeMillis())
        return ListenableWorker.Result.success()
    }
}