package hr.zvargovic.goldbtcwear.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.zvargovic.goldbtcwear.data.*
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen
import hr.zvargovic.goldbtcwear.ui.model.PriceService
import hr.zvargovic.goldbtcwear.ui.settings.SetupScreen
import hr.zvargovic.goldbtcwear.util.MarketUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

private const val TAG_APP = "APP"

// Mora biti usklađeno s TdCorrWorker-om
private const val MIN_OPEN_MS   = 30 * 60 * 1000L     // 30 min dok je tržište otvoreno
private const val MIN_CLOSED_MS = 3 * 60 * 60 * 1000L // 3 h dok je tržište zatvoreno

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val ctx = LocalContext.current
    val alertsStore   = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val corrStore     = remember { CorrectionStore(ctx) }
    val quotaStore    = remember { RequestQuotaStore(ctx) }
    val yahoo         = remember { YahooService() }
    val scope         = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }
    var spot by remember { mutableStateOf(2315.40) }

    val activeService = PriceService.Yahoo

    val corrPct by corrStore.corrFlow.collectAsState(initial = 0.0)
    val corrAge by corrStore.ageFlow.collectAsState(initial = 0L)

    // Za desnu libelu
    val reqUsedThisMonth by quotaStore.monthlyUsedFlow.collectAsState(initial = 0)
    val lastRunMs by quotaStore.lastRunMsFlow.collectAsState(initial = 0L)
    val REQ_QUOTA = 800 // free plan mjesečno

    val apiKey by settingsStore.apiKeyFlow.collectAsState(initial = null)
    val alarmEnabled by settingsStore.alarmEnabledFlow.collectAsState(initial = false)

    fun mask(s: String?): String =
        if (s.isNullOrBlank()) "(null)" else s.take(3) + "…" + s.takeLast(minOf(4, s.length))

    LaunchedEffect(apiKey) {
        Log.i(TAG_APP, "apiKey(DataStore)=${mask(apiKey)} len=${apiKey?.length ?: 0}")
    }
    LaunchedEffect(alarmEnabled) {
        Log.i(TAG_APP, "settings: alarmEnabled=$alarmEnabled")
    }

    fun minutesSince(tsMs: Long): Long =
        if (tsMs <= 0L) Long.MAX_VALUE
        else TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - tsMs)

    // Starost K (u minutama)
    var ageMin by remember { mutableStateOf(minutesSince(corrAge)) }
    LaunchedEffect(corrAge) {
        ageMin = minutesSince(corrAge)
        while (true) {
            delay(60_000)
            ageMin = minutesSince(corrAge)
        }
    }

    // Ticker “sada” da bi se ETA osvježavao svake minute
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }

    fun formatK(pct: Double): String =
        String.format(Locale.US, if (pct >= 0) "K:+%.2f%%" else "K:%.2f%%", pct * 100)

    fun formatEta(ms: Long): String {
        if (ms <= 0L) return "any time"
        val totalMin = ((ms + 59_999) / 60_000).toInt() // ceil na minute
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * Gruba, ali dovoljna procjena sljedećeg otvaranja tržišta:
     * - Koristi MarketUtils.isMarketOpenUtc(...)
     * - Ako je zatvoreno, napreduje po 1 min do max 7 dana unaprijed
     *   (pokriva dnevnu pauzu 22–23 UTC i vikend).
     */
    fun nextMarketOpenFrom(now: Long): Long {
        if (MarketUtils.isMarketOpenUtc(now)) return now
        var t = now
        val limit = now + 7 * 24 * 60 * 60 * 1000L
        while (t < limit && !MarketUtils.isMarketOpenUtc(t)) {
            t += 60_000L // +1 min
        }
        return t
    }

    /**
     * ETA = max( lastRun + (minGap ovisno o open/closed u TRENUTKU POKRETANJA),
     *            sljedeći trenutak kad tržište bude otvoreno )
     * Ako lastRun nije poznat (0), onda je jedini uvjet “tržište mora biti otvoreno”.
     */
    fun computeEtaMs(now: Long, lastRun: Long): Long {
        val isOpenNow = MarketUtils.isMarketOpenUtc(now)
        val minGap = if (isOpenNow) MIN_OPEN_MS else MIN_CLOSED_MS
        val nextByThrottle = if (lastRun <= 0L) now else lastRun + minGap
        val nextByMarket   = nextMarketOpenFrom(now)
        val earliest = max(nextByThrottle, nextByMarket)
        return (earliest - now).coerceAtLeast(0L)
    }

    // Badge “K:+x.xx% • XXm • next YY”
    val badgeTextStr = remember(corrPct, ageMin, lastRunMs, nowMs) {
        val minsText = if (ageMin == Long.MAX_VALUE) "n/a" else "${ageMin}m"
        val etaText = formatEta(computeEtaMs(nowMs, lastRunMs))
        "${formatK(corrPct)} • $minsText • next $etaText"
    }

    // Učitavanje lokalnih podataka (alerts/selected)
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()
        Log.i(TAG_APP, "init: alerts=${alerts.size}, selected=${selectedAlert.value}")
    }

    // Yahoo ticker + primjena K
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
                        "price: yahooRawEUR=${"%.2f".format(raw)}  K=${"%.4f".format(k)}  spot=${"%.2f".format(corrected)}"
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

                spot = spot,
                activeService = activeService,
                onToggleService = {
                    Log.i(TAG_APP, "toggle ignored (TD radi u workeru, UI je Yahoo×K)")
                },

                statusBadge = badgeTextStr,

                // Desna libela: potrošnja req-a
                reqUsedThisMonth = reqUsedThisMonth,
                reqMonthlyQuota = REQ_QUOTA
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
                        // 1) DataStore
                        settingsStore.saveAll(apiKey = key, alarmEnabled = alarm)
                        Log.i(TAG_APP, "settings saved -> apiKey=${mask(key)}, alarm=$alarm")
                        // 2) SharedPrefs za Workera (fallback)
                        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit().putString("api_key", key).apply()
                        Log.i(TAG_APP, "SharedPrefs(settings).api_key = ${mask(key)}")
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}