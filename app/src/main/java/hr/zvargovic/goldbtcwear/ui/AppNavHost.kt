package hr.zvargovic.goldbtcwear.ui

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.zvargovic.goldbtcwear.data.AlertsStore
import hr.zvargovic.goldbtcwear.data.SelectedAlertStore
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.data.CorrectionStore
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen
import hr.zvargovic.goldbtcwear.ui.model.PriceService
import hr.zvargovic.goldbtcwear.ui.settings.SetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG_APP = "APP"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // --- Store-ovi / servisi ---
    val ctx = LocalContext.current
    val alertsStore   = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val corrStore     = remember { CorrectionStore(ctx) }   // korekcija + timestamp
    val yahoo         = remember { YahooService() }
    val scope         = rememberCoroutineScope()

    // --- UI state ---
    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }
    var spot by remember { mutableStateOf(2315.40) }         // prikaz u EUR (korigirano)

    // >>> po planu: UI JE UVIJEK YAHOO (TD radi u workeru)
    val activeService = PriceService.Yahoo

    // --- Corr badge state ---
    val corrPct by corrStore.corrFlow.collectAsState(initial = 0.0)  // npr. 0.0123
    val corrAge by corrStore.ageFlow.collectAsState(initial = 0L)    // epoch ms

    fun minutesSince(tsMs: Long): Long =
        if (tsMs <= 0L) Long.MAX_VALUE
        else TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - tsMs)

    // ----- POČETAK: badge timer + format -----
    var ageMin by remember { mutableStateOf(minutesSince(corrAge)) }

    LaunchedEffect(corrAge) {
        ageMin = minutesSince(corrAge)
        while (true) {
            delay(60_000)
            ageMin = minutesSince(corrAge)
        }
    }

    fun formatK(pct: Double): String =
        String.format(Locale.US, if (pct >= 0) "K:+%.2f%%" else "K:%.2f%%", pct * 100)

    val badgeTextStr = remember(corrPct, ageMin) {
        val minsText = if (ageMin == Long.MAX_VALUE) "n/a" else "${ageMin}m"
        "${formatK(corrPct)} • $minsText"
    }
    // ----- KRAJ: badge timer + format -----

    // Učitaj lokalne liste/odabire
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()
        Log.i(TAG_APP, "init: alerts=${alerts.size}, selected=${selectedAlert.value}")
    }

    // Periodički dohvat cijene SAMO iz Yahoo-a (svakih ~20 s), pa primijeni K
    LaunchedEffect(corrPct) {
        Log.i(TAG_APP, "ticker: start Yahoo loop (K=${"%.4f".format(corrPct)})")
        while (true) {
            val result = yahoo.getSpotEur()
            result.onSuccess { raw ->
                val k = 1.0 + corrPct
                val corrected = raw * k
                if (corrected.isFinite() && corrected > 0.0) {
                    spot = corrected
                    Log.d(
                        TAG_APP,
                        "price: yahooRaw=${"%.2f".format(raw)}  K=${"%.4f".format(k)}  spot=${"%.2f".format(corrected)}"
                    )
                }
            }.onFailure { e ->
                Log.w(TAG_APP, "price yahoo failed: ${e.message}")
            }
            delay(20_000)
        }
    }

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") },
                onOpenSetup  = { navController.navigate("setup") },

                alerts = alerts,
                selectedAlert = selectedAlert.value,
                onSelectAlert = { v ->
                    selectedAlert.value = v
                    scope.launch { selectedStore.save(v) }
                },

                // prikaz: uvijek YAHOO × K
                spot = spot,
                activeService = activeService,

                // desna libela više NE prebacuje servis (no-op + log)
                onToggleService = {
                    Log.i(TAG_APP, "toggle ignored (TD radi u workeru, UI je Yahoo×K)")
                },

                // badge ispod spota
                statusBadge = badgeTextStr
            )
        }

        composable("alerts") {
            AlertsScreen(
                onBack = { navController.popBackStack(); Unit },
                onAdd = { navController.navigate("addAlert") },
                onDelete = { price ->
                    alerts.remove(price)
                    scope.launch { alertsStore.save(alerts.toList()) }
                    if (selectedAlert.value != null && abs(selectedAlert.value!! - price) < 0.0001) {
                        selectedAlert.value = null
                        scope.launch { selectedStore.save(null) }
                    }
                },
                alerts = alerts,
                spot = spot
            )
        }

        composable("addAlert") {
            AddAlertScreen(
                spot = spot,
                onBack = { navController.popBackStack(); Unit },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { abs(it - value) < 0.0001 }
                    ) {
                        alerts.add(value)
                        alerts.sort()
                        scope.launch { alertsStore.save(alerts.toList()) }
                    }
                    navController.popBackStack(); Unit
                }
            )
        }

        composable("setup") {
            SetupScreen(
                apiKey = "",          // po želji veži na SettingsStore.flow-ove
                alarmEnabled = false, // isto
                onBack = { navController.popBackStack() },
                onSave = { key, alarm ->
                    scope.launch { settingsStore.saveAll(apiKey = key, alarmEnabled = alarm) }
                    navController.popBackStack()
                }
            )
        }
    }
}