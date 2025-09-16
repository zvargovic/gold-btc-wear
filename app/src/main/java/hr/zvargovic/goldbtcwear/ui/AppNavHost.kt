package hr.zvargovic.goldbtcwear.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.zvargovic.goldbtcwear.R
import hr.zvargovic.goldbtcwear.alarm.AlarmService
import hr.zvargovic.goldbtcwear.data.*
import hr.zvargovic.goldbtcwear.data.api.YahooService
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen
import hr.zvargovic.goldbtcwear.presentation.MainActivity
import hr.zvargovic.goldbtcwear.ui.model.PriceService
import hr.zvargovic.goldbtcwear.ui.settings.SetupScreen
import hr.zvargovic.goldbtcwear.workers.TdCorrWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// --- MARKET STATUS (CLOSED + countdown) ---
import hr.zvargovic.goldbtcwear.util.MarketHours
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.unit.sp
// ------------------------------------------

// ★ Novo: konačna korekcija (fiksni + %) na FINALNU cijenu
import hr.zvargovic.goldbtcwear.domain.usecase.PriceAdjuster

private const val TAG_APP = "APP"
private const val CHANNEL_ALERTS = "alerts"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val ctx = LocalContext.current
    val alertsStore   = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val corrStore     = remember { CorrectionStore(ctx) }
    val quotaStore    = remember { RequestQuotaStore(ctx) }
    val spotStore     = remember { SpotStore(ctx) }
    val rsiStore      = remember { RsiStore(ctx) }
    val yahoo         = remember { YahooService() }
    val scope         = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }
    var spot by remember { mutableStateOf(2315.40) }

    val activeService = PriceService.Yahoo

    // Legacy K (TdCorrWorker): i dalje računamo raw×(1+K), ALI zatim primjenjujemo hardkod korekciju.
    val corrPct by corrStore.corrFlow.collectAsState(initial = 0.0)

    val dayUsed by quotaStore.dayUsedFlow.collectAsState(initial = 0)
    val reqLimit = RequestQuotaStore.DAILY_LIMIT

    val apiKey by settingsStore.apiKeyFlow.collectAsState(initial = null)
    val alarmEnabled by settingsStore.alarmEnabledFlow.collectAsState(initial = false)

    val refSpot by spotStore.refSpotFlow.collectAsState(initial = null)
    val lastSpot by spotStore.lastSpotFlow.collectAsState(initial = null)

    val rsiPeriod = 14
    var rsi by remember { mutableStateOf<Float?>(null) }
    val closes = remember { mutableStateListOf<Double>() }

    // --- STATUS TRŽIŠTA (open/closed) + countdown ---
    var marketOpen by remember { mutableStateOf(true) }
    var closedLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val st  = MarketHours.status()
            marketOpen = st.isOpen
            closedLine = if (!marketOpen) {
                val now  = Instant.now()
                val secs = Duration.between(now, st.nextChange).seconds.coerceAtLeast(0)
                val h = (secs / 3600).toInt()
                val m = ((secs % 3600) / 60).toInt()
                ctx.getString(R.string.market_closed_opens_in, h, m)
            } else null
            delay(30_000)
        }
    }
    // -------------------------------------------------

    fun mask(s: String?): String =
        if (s.isNullOrBlank()) "(null)" else s.take(3) + "…" + s.takeLast(minOf(4, s.length))

    LaunchedEffect(apiKey) { Log.i(TAG_APP, "apiKey(DataStore)=${mask(apiKey)} len=${apiKey?.length ?: 0}") }
    LaunchedEffect(alarmEnabled) { Log.i(TAG_APP, "settings: alarmEnabled=$alarmEnabled") }

    // ---- KEY GATE ----
    var needsKey by remember { mutableStateOf(false) }
    LaunchedEffect(apiKey) {
        val v = apiKey?.trim().orEmpty()
        needsKey = v.isEmpty()
        if (!needsKey && navController.currentDestination?.route == "setup") {
            navController.popBackStack() // back to gold
        }
    }

    // Worker
    LaunchedEffect(Unit) { TdCorrWorker.scheduleNext(ctx, "app-compose-start") }

    // Init: alerts + RSI bootstrap
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()

        val boot = rsiStore.loadAll()
        closes.clear(); closes.addAll(boot.takeLast(200))
        computeRsi(closes, rsiPeriod)?.let { rsi = it }
    }

    // Yahoo ticker: RAW → (×(1+K)) → PriceAdjuster(final) → shown; spremi SHOWN
    LaunchedEffect(corrPct) {
        while (true) {
            val result = yahoo.getSpotEur()
            result.onSuccess { raw ->
                val withK = raw * (1.0 + corrPct)            // legacy K s TD
                val shown = PriceAdjuster.apply(withK)        // HARDKOD FINAL

                if (shown.isFinite() && shown > 0.0) {
                    spot = shown

                    // spremamo SHOWN (ekran = tile = zadnje poznato)
                    scope.launch { spotStore.saveLast(shown) }
                    if (refSpot == null) scope.launch { spotStore.setRef(shown) }

                    closes += shown
                    while (closes.size > 200) closes.removeAt(0)
                    scope.launch { rsiStore.saveAll(closes.toList(), maxItems = 200) }
                    computeRsi(closes, rsiPeriod)?.let { rsi = it }

                    hr.zvargovic.goldbtcwear.tile.GoldTileService.requestUpdate(ctx)
                }
            }
            delay(20_000)
        }
    }

    // Helperi: vibracija + notifikacija
    fun vibrateStrong(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 350, 120, 350), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 350, 120, 350), -1)
                    )
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(600)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG_APP, "vibrate failed: ${t.message}")
        }
    }

    fun postAlertNotification(context: Context, hitAt: Double, current: Double) {
        val openIntent = Intent(context, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(context, 1001, openIntent, flags)

        val title = context.getString(R.string.app_name)
        val text = context.getString(
            R.string.alert_hit_text,
            "€" + "%,.2f".format(hitAt),
            "€" + "%,.2f".format(current)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        try {
            NotificationManagerCompat.from(context).notify(2001, builder.build())
        } catch (t: Throwable) {
            Log.w(TAG_APP, "notify failed: ${t.message}")
        }
    }

    // Poštuj Alarm ON/OFF postavku
    fun triggerAlertHit(context: Context, previousSelected: Double?, currentSpot: Double) {
        if (alarmEnabled) {
            AlarmService.start(context)
        } else {
            postAlertNotification(context, previousSelected ?: currentSpot, currentSpot)
        }
    }

    // ===== UI navigacija =====
    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            LaunchedEffect(needsKey) {
                if (needsKey) {
                    navController.navigate("setup") {
                        popUpTo("gold") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }

            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") },
                onOpenSetup  = { navController.navigate("setup") },

                alerts = alerts,
                selectedAlert = selectedAlert.value,
                onSelectAlert = { v ->
                    val prev = selectedAlert.value
                    selectedAlert.value = v
                    scope.launch { selectedStore.save(v) }
                    if (prev != null && v == null) triggerAlertHit(ctx, prev, spot)
                },

                spot = spot,
                activeService = activeService,
                onToggleService = { /* no-op: UI je Yahoo×K×final */ },

                refSpot = refSpot,
                rsi = rsi,

                onSetRefSpot = {
                    val v = spot
                    scope.launch { spotStore.setRef(v) }
                },

                reqUsedThisMonth = dayUsed,
                reqMonthlyQuota = reqLimit,

                // i dalje prikazujemo legacy K badge na skali
                kFactorPct = corrPct,
                kTextExtraStep = +8,
                kTextRadialOffsetPx = 0f
            )

            // Jednolinijski badge
            if (closedLine != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .offset(y = -2.dp)
                            .background(
                                color = Color(0x11FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = closedLine!!,
                            color = Color(0xFFF60303),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
            }

            if (needsKey) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.enter_api_key_overlay))
                }
            }
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
                        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .edit().putString("api_key", key).apply()
                    }
                    needsKey = key.isBlank()
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