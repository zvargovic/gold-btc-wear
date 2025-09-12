package hr.zvargovic.goldbtcwear.ui

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.zvargovic.goldbtcwear.data.*
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen
import hr.zvargovic.goldbtcwear.ui.model.PriceService
import hr.zvargovic.goldbtcwear.ui.settings.SetupScreen
import hr.zvargovic.goldbtcwear.workers.TdCorrWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG_APP = "APP"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val ctx = LocalContext.current
    val alertsStore   = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val corrStore     = remember { CorrectionStore(ctx) }
    val quotaStore    = remember { RequestQuotaStore(ctx) }
    val spotStore     = remember { SpotStore(ctx) }        // Spot i referenca
    val rsiStore      = remember { RsiStore(ctx) }         // Povijest za RSI
    val yahoo         = remember { YahooService() }
    val scope         = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }
    var spot by remember { mutableStateOf(2315.40) }

    // UI je uvijek Yahoo×K; toggle je samo vizual
    val activeService = PriceService.Yahoo

    val corrPct by corrStore.corrFlow.collectAsState(initial = 0.0)

    // Dnevni brojač requesta
    val dayUsed by quotaStore.dayUsedFlow.collectAsState(initial = 0)
    val reqLimit = RequestQuotaStore.DAILY_LIMIT

    val apiKey by settingsStore.apiKeyFlow.collectAsState(initial = null)
    val alarmEnabled by settingsStore.alarmEnabledFlow.collectAsState(initial = false)

    // Ref i zadnji spot
    val refSpot by spotStore.refSpotFlow.collectAsState(initial = null)
    val lastSpot by spotStore.lastSpotFlow.collectAsState(initial = null)

    // === RSI stanje ===
    val rsiPeriod = 14
    var rsi by remember { mutableStateOf<Float?>(null) }
    val closes = remember { mutableStateListOf<Double>() }

    fun mask(s: String?): String =
        if (s.isNullOrBlank()) "(null)" else s.take(3) + "…" + s.takeLast(minOf(4, s.length))

    LaunchedEffect(apiKey) { Log.i(TAG_APP, "apiKey(DataStore)=${mask(apiKey)} len=${apiKey?.length ?: 0}") }
    LaunchedEffect(alarmEnabled) { Log.i(TAG_APP, "settings: alarmEnabled=$alarmEnabled") }

    // Kickstart Workera
    LaunchedEffect(Unit) { TdCorrWorker.scheduleNext(ctx, "app-compose-start") }

    // Init: alerti + RSI bootstrap
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()
        Log.i(TAG_APP, "init: alerts=${alerts.size}, selected=${selectedAlert.value}")

        val boot = rsiStore.loadAll()
        closes.clear(); closes.addAll(boot.takeLast(200))
        computeRsi(closes, rsiPeriod)?.let { r ->
            rsi = r
            Log.i(TAG_APP, "RSI bootstrap=${"%.1f".format(r)} (n=${closes.size})")
        }
    }

    // Yahoo ticker + K + spremanje + RSI feed
    LaunchedEffect(corrPct) {
        Log.i(TAG_APP, "ticker: Yahoo loop (K=${"%.4f".format(corrPct)})")
        while (true) {
            val result = yahoo.getSpotEur()
            result.onSuccess { raw ->
                val k = 1.0 + corrPct
                val corrected = raw * k
                if (corrected.isFinite() && corrected > 0.0) {
                    spot = corrected
                    Log.d(TAG_APP, "price: raw=${"%.2f".format(raw)} K=${"%.4f".format(k)} spot=${"%.2f".format(corrected)}")

                    // spot store
                    scope.launch { spotStore.saveLast(corrected) }
                    if (refSpot == null) scope.launch { spotStore.setRef(corrected) }

                    // --- RSI feed + LOG update ---
                    closes += corrected
                    while (closes.size > 200) closes.removeAt(0)
                    scope.launch { rsiStore.saveAll(closes.toList(), maxItems = 200) }

                    val newRsi = computeRsi(closes, rsiPeriod)
                    if (newRsi != null) {
                        val prev = rsi
                        rsi = newRsi
                        if (prev == null || kotlin.math.abs(newRsi - prev) >= 0.1f) {
                            Log.i(TAG_APP, "RSI update=${"%.1f".format(newRsi)} (n=${closes.size})")
                        } else {
                            Log.d(TAG_APP, "RSI unchanged=${"%.1f".format(newRsi)}")
                        }
                    } else {
                        Log.d(TAG_APP, "RSI pending (need ${rsiPeriod + 1} closes), n=${closes.size}")
                    }
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

                spot = spot,
                activeService = activeService,
                onToggleService = {
                    Log.i(TAG_APP, "toggle ignored (TD u workeru, UI = Yahoo×K)")
                },

                // Referentni spot za postotak
                refSpot = refSpot,

                // *** PROSLJEĐUJEMO REALNI RSI ***
                rsi = rsi,

                // === OVO JE KLJUČ ZA TVOJ ERROR ===
                // GoldStaticScreen trenutno očekuje onSetRefSpot -> postavi ref na trenutačni spot
                onSetRefSpot = {
                    val v = spot
                    scope.launch { spotStore.setRef(v) }
                    Log.i(TAG_APP, "Ref reset to current spot=${"%.2f".format(v)}")
                },

                // Desna libela: dnevni req counter
                reqUsedThisMonth = dayUsed,
                reqMonthlyQuota = reqLimit,

                // K-faktor
                kFactorPct = corrPct,
                kTextExtraStep = +8,
                kTextRadialOffsetPx = 0f
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
                apiKey = apiKey ?: "",
                alarmEnabled = alarmEnabled,
                onBack = { navController.popBackStack() },
                onSave = { key, alarm ->
                    scope.launch {
                        settingsStore.saveAll(apiKey = key, alarmEnabled = alarm)
                        Log.i(TAG_APP, "settings saved -> apiKey=${mask(key)}, alarm=$alarm")
                        // fallback za Workera
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .edit().putString("api_key", key).apply()
                        Log.i(TAG_APP, "SharedPrefs(settings).api_key = ${mask(key)}")
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}

/* ===== RSI (period 14) ===== */
private fun computeRsi(closes: List<Double>, period: Int = 14): Float? {
    val n = closes.size
    if (n < period + 1) return null
    val last = closes.takeLast(period + 1)
    var gains = 0.0
    var losses = 0.0
    for (i in 1 until last.size) {
        val d = last[i] - last[i - 1]
        if (d >= 0) gains += d else losses += -d
    }
    val avgGain = gains / period
    val avgLoss = losses / period
    if (!avgGain.isFinite() || !avgLoss.isFinite()) return null
    val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
    val rsi = 100.0 - 100.0 / (1.0 + rs)
    val safe = when {
        rsi.isNaN() || rsi.isInfinite() -> if (avgLoss == 0.0 && avgGain > 0.0) 100.0 else 50.0
        else -> rsi
    }
    return safe.coerceIn(0.0, 100.0).toFloat()
}