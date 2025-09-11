package hr.zvargovic.goldbtcwear

import android.app.Application
import hr.zvargovic.goldbtcwear.workers.TdCorrWorker

class GoldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kick jednom pri startu appa — dalje se worker sam re-schedula.
        TdCorrWorker.scheduleNext(this, reason = "app-onCreate")
    }
}