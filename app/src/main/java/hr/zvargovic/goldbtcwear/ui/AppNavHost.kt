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
import kotlin.math.max

private const val TAG_APP = "APP"

// ====== RSI persistence (SharedPreferences) ======
private const val PREF_RSI = "rsi_state"
private const val KEY_CLOSES = "closes_csv"
private const val KEY_AG = "avg_gain"
private const val KEY_AL = "avg_loss"
private const val KEY_INIT = "initialized"

private data class RsiState(
    val period: Int = 14,
    var initialized: Boolean = false,
    var avgGain: Double = 0.0,
    var avgLoss: Double = 0.0,
    var lastClose: Double? = null,
    var value: Float? = null
)

private fun loadRsiState(ctx: android.content.Context): Pair<RsiState, MutableList<Double>> {
    val sp = ctx.getSharedPreferences(PREF_RSI, android.content.Context.MODE_PRIVATE)
    val csv = sp.getString(KEY_CLOSES, "") ?: ""
    val closes = if (csv.isNotBlank())
        csv.split(',').mapNotNull { it.toDoubleOrNull() }.toMutableList()
    else mutableListOf()

    val st = RsiState(
        initialized = sp.getBoolean(KEY_INIT, false),
        avgGain = sp.getString(KEY_AG, null)?.toDoubleOrNull() ?: 0.0,
        avgLoss = sp.getString(KEY_AL, null)?.toDoubleOrNull() ?: 0.0,
        lastClose = closes.lastOrNull(),
        value = null
    )
    return st to closes
}

private fun saveRsiState(
    ctx: android.content.Context,
    st: RsiState,
    closes: List<Double>,
    maxKeep: Int = 120
) {
    val sp = ctx.getSharedPreferences(PREF_RSI, android.content.Context.MODE_PRIVATE)
    val clipped = closes.takeLast(maxKeep)
    val csv = clipped.joinToString(",")
    sp.edit()
        .putString(KEY_CLOSES, csv)
        .putString(KEY_AG, st.avgGain.toString())
        .putString(KEY_AL, st.avgLoss.toString())
        .putBoolean(KEY_INIT, st.initialized)
        .apply()
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val ctx = LocalContext.current
    val alertsStore   = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val corrStore     = remember { CorrectionStore(ctx) }
    val quotaStore    = remember { RequestQuotaStore(ctx) }
    val spotStore     = remember { SpotStore(ctx) }        // [SPOTSTORE]
    val yahoo         = remember { YahooService() }
    val scope         = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }
    var spot by remember { mutableStateOf(2315.40) }

    // UI je uvijek Yahoo×K; toggle na desnoj libeli je samo vizual
    val activeService = PriceService.Yahoo

    val corrPct by corrStore.corrFlow.collectAsState(initial = 0.0)

    // Dnevni brojač requesta (TD ~800/day)
    val dayUsed by quotaStore.dayUsedFlow.collectAsState(initial = 0)
    val reqLimit = RequestQuotaStore.DAILY_LIMIT

    val apiKey by settingsStore.apiKeyFlow.collectAsState(initial = null)
    val alarmEnabled by settingsStore.alarmEnabledFlow.collectAsState(initial = false)

    // [SPOTSTORE] reference & last flows
    val refSpot by spotStore.refSpotFlow.collectAsState(initial = null)
    val lastSpot by spotStore.lastSpotFlow.collectAsState(initial = null)

    // ===== RSI engine (in-App, bez diranja GoldLayouta) =====
    // - radi odmah (seed na 50), pa se pegla live tickovima
    // - persistira u SharedPrefs (closes + avgGain/avgLoss + init)
    val rsiStateAndCloses = remember { loadRsiState(ctx) }
    val rsiState = remember { rsiStateAndCloses.first }
    val closes   = remember { rsiStateAndCloses.second }
    var rsiUi by remember { mutableStateOf<Float?>(rsiState.value) }

    // ako ništa nemamo, postavi neutral seed (RSI ~ 50) da UI nije “prazan”
    LaunchedEffect(Unit) {
        if (!rsiState.initialized && closes.isEmpty()) {
            // seed s trenutnim spotom kad ga dobijemo prvi put dolje u tickeru
            // (ovdje samo označimo da možemo inic. čim stigne close)
            rsiState.initialized = false
            rsiState.avgGain = 0.0
            rsiState.avgLoss = 0.0
            rsiState.lastClose = null
            rsiUi = 50f
        } else {
            // ako imamo spremljene podatke, pokušaj izračunati trenutni RSI
            if (rsiState.initialized && rsiState.avgLoss >= 0.0 && rsiState.avgGain >= 0.0) {
                val rs = if (rsiState.avgLoss == 0.0) Double.POSITIVE_INFINITY
                else rsiState.avgGain / rsiState.avgLoss
                val rsi = if (rs.isInfinite()) 100.0 else 100.0 - (100.0 / (1.0 + rs))
                rsiUi = rsi.toFloat().coerceIn(0f, 100f)
            } else if (closes.size >= rsiState.period + 1) {
                // imamo dovoljno closes, napravi inicijalni Wilder
                var gains = 0.0
                var losses = 0.0
                for (i in 1..rsiState.period) {
                    val diff = closes[i] - closes[i - 1]
                    if (diff >= 0) gains += diff else losses += -diff
                }
                rsiState.avgGain = gains / rsiState.period
                rsiState.avgLoss = losses / rsiState.period
                rsiState.initialized = true
                rsiState.lastClose = closes.last()
                val rs = if (rsiState.avgLoss == 0.0) Double.POSITIVE_INFINITY
                else rsiState.avgGain / rsiState.avgLoss
                val rsi = if (rs.isInfinite()) 100.0 else 100.0 - (100.0 / (1.0 + rs))
                rsiUi = rsi.toFloat().coerceIn(0f, 100f)
            } else {
                rsiUi = 50f
            }
        }
    }

    fun mask(s: String?): String =
        if (s.isNullOrBlank()) "(null)" else s.take(3) + "…" + s.takeLast(minOf(4, s.length))

    LaunchedEffect(apiKey) {
        Log.i(TAG_APP, "apiKey(DataStore)=${mask(apiKey)} len=${apiKey?.length ?: 0}")
    }
    LaunchedEffect(alarmEnabled) {
        Log.i(TAG_APP, "settings: alarmEnabled=$alarmEnabled")
    }

    // Kickstart Workera kad se UI digne
    LaunchedEffect(Unit) {
        TdCorrWorker.scheduleNext(ctx, "app-compose-start")
    }

    // Yahoo ticker + primjena K + spremanje u SpotStore + RSI update
    LaunchedEffect(corrPct) {
        Log.i(TAG_APP, "ticker: Yahoo loop (K=${"%.4f".format(corrPct)})")
        var saveDebounce = 0
        while (true) {
            val result = yahoo.getSpotEur()
            result.onSuccess { raw ->
                val k = 1.0 + corrPct
                val corrected = raw * k
                if (corrected.isFinite() && corrected > 0.0) {
                    spot = corrected
                    Log.d(
                        TAG_APP,
                        "price: yahooRawEUR=${"%.2f".format(raw)}  K=${"%.4f".format(k)}  spot=${"%.2f".format(corrected)}"
                    )
                    // [SPOTSTORE] spremi zadnji spot
                    scope.launch { spotStore.saveLast(corrected) }
                    // [SPOTSTORE] inicijaliziraj ref ako nije postavljen
                    if (refSpot == null) {
                        scope.launch { spotStore.setRef(corrected) }
                    }

                    // ---- RSI update (Wilder) ----
                    val close = corrected
                    // popuni seed ako ništa nemamo
                    if (closes.isEmpty() && rsiState.lastClose == null) {
                        // seediraj 15 istih closeova → RSI=50 i odmah se pegla
                        repeat(rsiState.period + 1) { closes.add(close) }
                        rsiState.lastClose = close
                        rsiState.avgGain = 1e-6
                        rsiState.avgLoss = 1e-6
                        rsiState.initialized = true
                        rsiUi = 50f
                        saveDebounce = 0
                    } else {
                        // standardni tok
                        val prev = rsiState.lastClose ?: closes.lastOrNull()
                        if (prev != null && prev > 0.0) {
                            val diff = close - prev
                            val gain = if (diff > 0) diff else 0.0
                            val loss = if (diff < 0) -diff else 0.0

                            if (!rsiState.initialized && closes.size >= rsiState.period) {
                                // inicijalni Wilder preko prvih 14 perioda
                                var gains = 0.0
                                var losses = 0.0
                                // uzmi zadnjih 15 closeova (14 razlika)
                                val base = (closes + close).takeLast(rsiState.period + 1)
                                for (i in 1..rsiState.period) {
                                    val d = base[i] - base[i - 1]
                                    if (d >= 0) gains += d else losses += -d
                                }
                                rsiState.avgGain = gains / rsiState.period
                                rsiState.avgLoss = losses / rsiState.period
                                rsiState.initialized = true
                            } else if (rsiState.initialized) {
                                // Wilder smoothing
                                val p = rsiState.period.toDouble()
                                rsiState.avgGain = (rsiState.avgGain * (p - 1) + gain) / p
                                rsiState.avgLoss = (rsiState.avgLoss * (p - 1) + loss) / p
                            }

                            // RSI value
                            if (rsiState.initialized) {
                                val rs = if (rsiState.avgLoss == 0.0) Double.POSITIVE_INFINITY
                                else rsiState.avgGain / rsiState.avgLoss
                                val rsi = if (rs.isInfinite()) 100.0 else 100.0 - (100.0 / (1.0 + rs))
                                rsiUi = rsi.toFloat().coerceIn(0f, 100f)
                            } else {
                                rsiUi = 50f
                            }
                        } else {
                            rsiUi = 50f
                        }

                        // Održavaj kružni buffer closes (max 240)
                        closes.add(close)
                        if (closes.size > 240) closes.removeAt(0)
                        rsiState.lastClose = close
                    }

                    // Persistiraj RSI svaka ~3 ticka da ne pišemo stalno
                    saveDebounce++
                    if (saveDebounce >= 3) {
                        saveDebounce = 0
                        saveRsiState(ctx, rsiState, closes)
                    }
                }
            }.onFailure { e ->
                Log.w(TAG_APP, "price yahoo failed: ${e.message}")
            }
            delay(20_000)
        }
    }

    // Init lokalnih podataka
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()
        Log.i(TAG_APP, "init: alerts=${alerts.size}, selected=${selectedAlert.value}")
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
                    Log.i(TAG_APP, "toggle ignored (TD radi u workeru, UI je Yahoo×K)")
                },

                // [SPOTSTORE] prosljeđujemo refSpot u UI za točan % izračun
                refSpot = refSpot,

                // RSI izvana — sada stvarna, kontinuirana vrijednost (0..100), bez čekanja
                rsi = rsiUi,

                // Desna libela: dnevni req counter
                reqUsedThisMonth = dayUsed,
                reqMonthlyQuota = reqLimit,

                // K-faktor (prikaz na suprotnoj strani lijeve skale)
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