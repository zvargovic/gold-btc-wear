package hr.zvargovic.goldbtcwear.ui
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.withFrameNanos
import androidx.wear.compose.material.Text as WearText
import hr.zvargovic.goldbtcwear.R
import hr.zvargovic.goldbtcwear.ui.model.PriceService
import kotlin.math.*
import java.util.Locale
import android.graphics.BlurMaskFilter
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat

private inline fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

@Composable
fun GoldStaticScreen(
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    alerts: List<Double> = emptyList(),
    selectedAlert: Double? = null,
    onSelectAlert: (Double?) -> Unit = {},
    onOpenSetup: () -> Unit = {},

    // Živi spot i aktivni servis dolaze izvana
    spot: Double,
    activeService: PriceService,
    onToggleService: () -> Unit,

    // Opcionalni status badge (ako je null/blank -> ne crta se)
    statusBadge: String? = null,
    // [NOVO] za “Req: used/limit” u desnoj libeli
    reqUsedThisMonth: Int,
    reqMonthlyQuota: Int
) {
    val spotNow = spot
    val premiumPct = 0.0049
    val buy = spotNow * (1 + premiumPct)
    val sell = spotNow * (1 - premiumPct)

    val lastRequestPrice = 2312.0

    // Popup state
    var showPicker by remember { mutableStateOf(false) }

    // alertPrice se inicijalizira iz selectedAlert (preživljava restart)
    var alertPrice by remember(selectedAlert) { mutableStateOf<Double?>(selectedAlert) }
    val alertWindowEur = 30.0
    val alertHitToleranceEur = 0.10

    var alertAnchorSpot by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(alertPrice) {
        alertAnchorSpot = if (alertPrice != null) spotNow else null
    }
    LaunchedEffect(spotNow, alertPrice, alertAnchorSpot) {
        val ap = alertPrice ?: return@LaunchedEffect
        val anchor = alertAnchorSpot
        val closeEnough = kotlin.math.abs(spotNow - ap) <= alertHitToleranceEur
        val crossed = when {
            anchor == null -> closeEnough
            ap >= anchor -> spotNow >= ap || closeEnough
            else -> spotNow <= ap || closeEnough
        }
        if (crossed) {
            // “Ispalio” se ovaj alert – očisti i trajno
            alertPrice = null
            onSelectAlert(null)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }

    val waterTop = 0.15f * screenH
    val waterBot = 0.90f * screenH

    var tAnim by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last == 0L) last = now
            val dt = (now - last) / 1_000_000_000f
            last = now
            tAnim += dt * 0.30f
        }
    }

    val fill01: Float = alertPrice?.let { ap ->
        val dist = kotlin.math.abs(ap - spotNow)
        val f = 1.0 - (dist / alertWindowEur).coerceIn(0.0, 1.0)
        f.toFloat()
    } ?: 0f
    val goalPxRaw = lerpF(waterBot, waterTop, fill01)

    var yPx by remember { mutableStateOf(goalPxRaw) }
    var prevGoal by remember { mutableStateOf(goalPxRaw) }
    var prevTime by remember { mutableStateOf(tAnim) }

    var plateauLeft by remember { mutableStateOf(0f) }
    val plateauDuration = 0.9f

    var releaseLeft by remember { mutableStateOf(0f) }
    val releaseDuration = 0.85f

    val tauUp = 0.28f
    val tauDown = 0.50f
    val maxDropPerSec = 420f
    val wrapJumpThreshPx = screenH * 0.55f

    run {
        val dt = (tAnim - prevTime).coerceAtLeast(0f)
        prevTime = tAnim

        val goalNow = goalPxRaw
        val goalDelta = goalNow - prevGoal
        prevGoal = goalNow

        if (goalDelta > wrapJumpThreshPx) {
            plateauLeft = plateauDuration
            releaseLeft = releaseDuration
        }

        val nearTop = goalNow <= waterTop + 8f
        if (nearTop) plateauLeft = max(plateauLeft, 0.15f)

        val effectiveGoal = if (plateauLeft > 0f) min(yPx, goalNow) else goalNow

        val tau = if (effectiveGoal < yPx) tauUp else tauDown
        val k = (1f - exp(-dt / max(1e-4f, tau))).coerceIn(0f, 1f)
        var yProposed = yPx + (effectiveGoal - yPx) * k

        if (releaseLeft > 0f && yProposed > yPx) {
            val x = (1f - (releaseLeft / releaseDuration)).coerceIn(0f, 1f)
            val outCubic = 1f - (1f - x).pow(3)
            val relK = 0.25f + 0.75f * outCubic
            yProposed = yPx + (yProposed - yPx) * relK
        }

        val maxDown = maxDropPerSec * dt
        if (yProposed - yPx > maxDown) yProposed = yPx + maxDown

        yPx = yProposed
        plateauLeft = (plateauLeft - dt).coerceAtLeast(0f)
        releaseLeft = (releaseLeft - dt).coerceAtLeast(0f)
    }

    val headroom = (yPx / screenH).coerceIn(0f, 1f)
    val nearTopMul = 0.65f + 0.35f * headroom
    val releaseMul = if (releaseLeft > 0f) 0.85f else 1f
    val waveAmpMul = nearTopMul * releaseMul

    val yWaterPxForDrawing = yPx
    val yWaterPxForTriggers = yPx

    fun waterLevelRatio(): Float = (yWaterPxForDrawing / screenH).coerceIn(0f, 1f)

    val deltaHr = if (lastRequestPrice > 0) (spotNow - lastRequestPrice) / lastRequestPrice else 0.0
    val tick = 0.001
    val maxTicks = 5
    val rawSteps = (deltaHr / tick).roundToInt()
    val steps = rawSteps.coerceIn(-maxTicks, maxTicks)
    val overload = abs(deltaHr) > maxTicks * tick

    fun pctStr(p: Double): String {
        val sign = if (p >= 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", p * 100)}%"
    }
    fun euro(amount: Double): String = "€" + String.format(Locale.US, "%,.2f", amount)

    // Blink bez animateFloat/LinearEasing — čisto preko sinusa i tAnim
    val blinkAlpha: Float = run {
        // ~0.7 Hz treperenje: mapiramo sin u [0.25, 1.0]
        val s = ((sin(tAnim * 2.0f * Math.PI.toFloat() * 0.7f) + 1f) * 0.5f)
        0.25f + 0.75f * s
    }
    val markerAlpha = if (overload) blinkAlpha else 1f

    val orangeDim   = Color(0x66FF7A00)
    val orangeLine  = Color(0xFFFF7A00)
    val orangeLite  = Color(0xFFFFA040)
    val warmWhite   = Color(0xFFDCD3C8)

    val buyTint  = Color(0xFF2FBF6B)
    val sellTint = Color(0xFFE0524D)

    val bubbleStep by animateFloatAsState(
        targetValue = if (activeService == PriceService.TwelveData) 5f else -5f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bubbleStep"
    )

    val bottomTargetStep = when {
        alertPrice == null     -> 0f
        alertPrice!! > spotNow ->  maxTicks.toFloat()
        alertPrice!! < spotNow -> -maxTicks.toFloat()
        else                   -> 0f
    }
    val bottomBubbleStep by animateFloatAsState(
        targetValue = bottomTargetStep,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bottomBubbleStep"
    )

    val res = LocalContext.current.resources
    val iconTop = remember { ImageBitmap.imageResource(res, R.drawable.ic_yahoo) }
    val iconBottom = remember { ImageBitmap.imageResource(res, R.drawable.ic_twelve) }

    // demo RSI kombinacija (anim.)
    val rsiTD = (50f + 25f * sin((tAnim * 0.85f).toDouble()).toFloat()).coerceIn(0f, 100f)
    val rsiYahoo = (50f + 25f * sin((tAnim * 0.65f + 1.1f).toDouble()).toFloat()).coerceIn(0f, 100f)
    val rsiCombinedTarget = ((rsiTD + rsiYahoo) * 0.5f).coerceIn(0f, 100f)
    val rsiAnimated by animateFloatAsState(
        targetValue = rsiCombinedTarget,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "rsiAnimated"
    )

    // ======= CRTANJE =======
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1) MASKA + VODA + WEBP
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()

                    val w = size.width
                    val h = size.height

                    val levelY = (yWaterPxForDrawing / screenH) * h

                    val ampBase = 18f * waveAmpMul
                    val ampChop = 4.0f * waveAmpMul
                    val lenLong = w / 1.35f
                    val lenMid  = w / 0.95f
                    val lenShort = w / 0.36f
                    val phaseL = tAnim * 0.45f
                    val phaseM = tAnim * 0.9f + 1.1f
                    val phaseS = tAnim * 1.6f + 0.6f
                    val twoPi = (Math.PI * 2).toFloat()

                    fun crestY(x: Float): Float =
                        levelY +
                                ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                                (ampBase * 0.55f) * sin((x / lenMid) * twoPi + phaseM) * 0.35f +
                                ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

                    val cutTop = Path().apply {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, crestY(w))
                        var x = w; val step = 4f
                        while (x >= 0f) { lineTo(x, crestY(x)); x -= step }
                        lineTo(0f, crestY(0f)); close()
                    }
                    drawPath(cutTop, Color.White, blendMode = BlendMode.DstOut)

                    val waterPath = Path().apply {
                        moveTo(0f, crestY(0f)); lineTo(w, crestY(w)); lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(
                        path = waterPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE2791C), Color(0xFF1A0A00)),
                            startY = levelY - 100f,
                            endY = h
                        ),
                        blendMode = BlendMode.Multiply
                    )

                    val depthBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.35f to Color(0x33220000),
                            1f to Color(0xAA110000)
                        )
                    )
                    drawRect(
                        brush = depthBrush,
                        topLeft = Offset(0f, levelY),
                        size = Size(w, h - levelY),
                        blendMode = BlendMode.Multiply
                    )

                    val crestPath = Path().apply {
                        moveTo(0f, crestY(0f))
                        var x = 0f; val step = 4f
                        while (x <= w) { lineTo(x, crestY(x)); x += step }
                    }
                    val feather = 28f
                    val featherBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color(0x33000000), orangeDim, Color.Transparent),
                        startY = levelY - feather, endY = levelY + feather
                    )
                    drawPath(crestPath, featherBrush, style = Stroke(width = feather, cap = StrokeCap.Butt))
                    drawPath(crestPath, Color(0x99FFFFFF), style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                    drawPath(crestPath, orangeLine.copy(alpha = 0.6f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.anim))
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                },
                update = { iv -> (iv.drawable as? AnimatedImageDrawable)?.start() }
            )
        }
        // 1b) mjehurići
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val levelY = (yWaterPxForDrawing / screenH) * h

            val ampBase = 18f * waveAmpMul
            val ampChop = 4.0f * waveAmpMul
            val lenLong = w / 1.35f
            val lenMid  = w / 0.95f
            val lenShort = w / 0.36f
            val phaseL = tAnim * 0.45f
            val phaseM = tAnim * 0.9f + 1.1f
            val phaseS = tAnim * 1.6f + 0.6f
            val twoPi = (Math.PI * 2).toFloat()

            fun crestY(x: Float): Float =
                levelY +
                        ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                        (ampBase * 0.55f) * sin((x / lenMid) * twoPi + phaseM) * 0.35f +
                        ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

            val stepX = 18f
            val riseMin = 22f
            val riseMax = 46f
            val life = 2.8f
            val tNorm = (tAnim / life)

            fun hash01(i: Int): Float {
                val x = ((i * 1664525) xor (i shl 13)) * 1013904223
                return ((x and 0x7fffffff) % 1000000) / 1000000f
            }

            var x = 0f
            while (x <= w) {
                val i = (x / stepX).roundToInt()
                val seed = hash01(i)
                val phase = (tNorm + seed * 0.73f)
                val frac = phase - floor(phase)
                val y0 = crestY(x) - 2f
                val e = (1f - (1f - frac) * (1f - frac))
                val rise = riseMin + (riseMax - riseMin) * (0.4f + 0.6f * seed)
                val y = y0 - e * rise

                if (y < levelY - 6f && y > h * 0.08f) {
                    val VIS = 0.25f
                    val addBlend = BlendMode.Plus

                    val spotPx = 30.sp.toPx()
                    fun flerp(a: Float, b: Float, t: Float) = a + (b - a) * t
                    val maxR = spotPx * 0.62f
                    val minR = maxR * 0.26f
                    val baseR = flerp(minR, maxR, seed)
                    val r = baseR * (0.85f + 0.35f * e)

                    val sway = (sin(((tAnim + seed * 7f) * 0.9f).toDouble()).toFloat()) * 2.0f
                    val c = Offset(x + sway, y)

                    drawCircle(
                        Color.White.copy(alpha = (0.20f * (1f - frac) * VIS * 0.35f).coerceAtMost(0.35f)),
                        r * 1.8f,
                        c.copy(y = c.y + r * 0.25f),
                        blendMode = addBlend
                    )

                    val aBase = (0.20f * (1f - frac) * VIS)

                    drawCircle(Color.White.copy(alpha = (aBase * 0.95f).coerceAtMost(0.85f)), r, c, blendMode = addBlend)
                    drawCircle(
                        Color.White.copy(alpha = (aBase * 1.2f).coerceAtMost(0.95f)),
                        r * 0.55f,
                        Offset(c.x + r * 0.38f, c.y - r * 0.40f),
                        blendMode = BlendMode.Plus
                    )

                    drawCircle(
                        Color.Black.copy(alpha = 0.10f), r * 1.04f, c,
                        style = Stroke(width = (r * 0.18f).coerceAtLeast(0.6f))
                    )

                    if (frac > 0.65f) {
                        val k = ((frac - 0.65f) / 0.35f).coerceIn(0f, 1f)
                        val dots = 3
                        val twoPi2 = twoPi
                        for (d in 0 until dots) {
                            val ang = (twoPi2 * (seed * 13f + d * 0.33f))
                            val rr = r * (1.6f + 0.6f * d)
                            val ox = cos(ang.toDouble()).toFloat() * rr * k
                            val oy = sin(ang.toDouble()).toFloat() * rr * k
                            drawCircle(
                                Color.White.copy(alpha = (aBase * (0.55f * (1f - k))).coerceAtMost(0.35f)),
                                r * (0.18f + 0.10f * d),
                                Offset(c.x + ox, c.y - 2f + oy),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                }
                x += stepX
            }
        }

        // 2) minutni krug
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f
            val rBezel = radius * 0.965f
            val dotR = 4.8f
            val dotHi = 0.9f

            for (i in 0 until 60) {
                val ang = Math.toRadians((i * 6f - 90f).toDouble()).toFloat()
                val x = cx + rBezel * cos(ang)
                val y = cy + rBezel * sin(ang)
                drawCircle(Color(0x55FF7A00), dotR, Offset(x, y))
                drawCircle(Color(0x88FFC07A), dotR * 0.55f, Offset(x + dotR * dotHi * 0.3f, y - dotR * dotHi * 0.35f))
            }
            val millis = System.currentTimeMillis()
            val minute = ((millis / 60_000L) % 60).toInt()
            val angDeg = minute * 6f - 90f
            val angRad = Math.toRadians(angDeg.toDouble()).toFloat()
            val px = cx + rBezel * cos(angRad)
            val py = cy + rBezel * sin(angRad)
            drawCircle(Color(0xFFDEAEE6), 6f, Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.8f), 2.5f, Offset(px + 0.8f, py - 0.8f))
        }

        // 3) lijeva skala
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val span = 50f
            val start = 180f - span / 2f
            val stepAng = span / (maxTicks * 2)

            val outer = radius * 0.920f
            val inner = radius * 0.860f

            for (i in -maxTicks..maxTicks) {
                val ang = Math.toRadians((start + (i + maxTicks) * stepAng).toDouble()).toFloat()
                val cosA = cos(ang); val sinA = sin(ang)
                val p1 = Offset(cx + inner * cosA, cy + inner * sinA)
                val p2 = Offset(cx + outer * cosA, cy + outer * sinA)
                val col = if (i == 0) orangeLite else orangeDim
                val sw = if (i == 0) 5.6f else 4.2f
                drawLine(col, p1, p2, strokeWidth = sw, cap = StrokeCap.Round)
            }

            val markerSteps = steps
            val markerAng = start + (markerSteps + maxTicks) * stepAng
            val a = Math.toRadians(markerAng.toDouble()).toFloat()
            val mInner = inner - 3f
            val mOuter = outer + 6f
            val p1 = Offset(cx + mInner * cos(a), cy + mInner * sin(a))
            val p2 = Offset(cx + mOuter * cos(a), cy + mOuter * sin(a))
            val markerColor = when {
                markerSteps > 0 -> buyTint
                markerSteps < 0 -> sellTint
                else -> warmWhite.copy(alpha = 0.85f)
            }
            drawLine(
                markerColor.copy(alpha = 0.75f * markerAlpha),
                p1, p2,
                strokeWidth = 7.6f,
                cap = StrokeCap.Round
            )

            val showPct = if (overload) (spotNow - lastRequestPrice) / lastRequestPrice else markerSteps * tick.toDouble()
            val txt = pctStr(showPct)
            val baseLabelColor = if (showPct >= 0) buyTint else sellTint
            val softLabel = androidx.compose.ui.graphics.lerp(warmWhite, baseLabelColor, 0.55f)
            val txtPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = ((softLabel.alpha * 255).roundToInt() shl 24) or
                        ((softLabel.red * 255).roundToInt() shl 16) or
                        ((softLabel.green * 255).roundToInt() shl 8) or
                        ((softLabel.blue * 255).roundToInt())
                textSize = 11.sp.toPx()
                alpha = (0.85f * markerAlpha * 255).toInt()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
            }
            val extraStep = -7
            val txtAng = start + (extraStep + maxTicks) * stepAng
            val angRad = Math.toRadians(txtAng.toDouble()).toFloat()
            val rText = (inner + outer) / 2f
            val cxT = cx + rText * cos(angRad)
            val cyT = cy + rText * sin(angRad)
            val fm = txtPaint.fontMetrics
            val baselineYOffset = -(fm.ascent + fm.descent) / 2f
            val halfTextW = txtPaint.measureText(txt) / 2f
            drawIntoCanvas { c ->
                c.save()
                c.translate(cxT, cyT)
                c.rotate(txtAng + 270f)
                c.nativeCanvas.drawText(txt, -halfTextW, baselineYOffset, txtPaint)
                c.restore()
            }
        }

        // 4) naslov/spot/buy/sell  — SPOT je klikabilan -> popup
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FollowWaterText(
                    id = "title",
                    text = "Gold (EUR/oz)",
                    txtColor = orangeLine,
                    fontSizeSp = 14,
                    weight = FontWeight.Normal,
                    t = tAnim,
                    waterLevel = waterLevelRatio(),
                    yOffset = 0.dp,
                    blurStrengthDp = 1.5.dp,
                    followWave = true,
                    activeOverride = if (yWaterPxForTriggers <= 95f) 1f else 0f
                )
                FollowWaterText(
                    id = "spot",
                    text = euro(spotNow),
                    txtColor = orangeLine,
                    fontSizeSp = 30,
                    weight = FontWeight.Bold,
                    t = tAnim,
                    waterLevel = waterLevelRatio(),
                    yOffset = (-20).dp,
                    blurStrengthDp = 2.dp,
                    followWave = false,
                    activeOverride = if (yWaterPxForTriggers <= 150f) 1f else 0f,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { showPicker = true })
                    }
                )

            }
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FollowWaterText(
                    id = "buy",
                    text = euro(buy),
                    txtColor = androidx.compose.ui.graphics.lerp(orangeLine, buyTint, 0.65f),
                    fontSizeSp = 18,
                    weight = FontWeight.SemiBold,
                    t = tAnim,
                    waterLevel = waterLevelRatio(),
                    yOffset = (-42).dp,
                    blurStrengthDp = 2.dp,
                    followWave = true,
                    activeOverride = if (yWaterPxForTriggers <= 265f) 1f else 0f
                )
                FollowWaterText(
                    id = "sell",
                    text = euro(sell),
                    txtColor = androidx.compose.ui.graphics.lerp(orangeLine, sellTint, 0.65f),
                    fontSizeSp = 18,
                    weight = FontWeight.SemiBold,
                    t = tAnim,
                    waterLevel = waterLevelRatio(),
                    yOffset = (-42).dp,
                    blurStrengthDp = 2.dp,
                    followWave = true,
                    activeOverride = if (yWaterPxForTriggers <= 300f) 1f else 0f
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // 5) donja libela
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val span = 50f
            val start = 90f - span / 2f
            val stepAng = span / (maxTicks * 2)
            val outer = radius * 0.920f
            val inner = radius * 0.860f

            val tubeR = (inner + outer) / 2f
            val tubeWidth = (outer - inner) * 1.80f
            val tubeRect = Rect(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)

            drawArc(
                color = Color(0xFF6F3511).copy(alpha = 0.45f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth * 0.30f, cap = StrokeCap.Round)
            )

            val startRad = Math.toRadians(start.toDouble()).toFloat()
            val endRad   = Math.toRadians((start + span).toDouble()).toFloat()
            val sx = cx + tubeR * cos(startRad)
            val sy = cy + tubeR * sin(startRad)
            val ex = cx + tubeR * cos(endRad)
            val ey = cy + tubeR * sin(endRad)

            val signTextSize = 11.sp.toPx() * 1.25f
            val minusPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(
                    (sellTint.alpha * 255).roundToInt(),
                    (sellTint.red * 255).roundToInt(),
                    (sellTint.green * 255).roundToInt(),
                    (sellTint.blue * 255).roundToInt()
                )
                textSize = signTextSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val plusPaint = android.graphics.Paint(minusPaint).apply {
                color = android.graphics.Color.argb(
                    (buyTint.alpha * 255).roundToInt(),
                    (buyTint.red * 255).roundToInt(),
                    (buyTint.green * 255).roundToInt(),
                    (buyTint.blue * 255).roundToInt()
                )
            }
            fun drawSign(text: String, x: Float, y: Float, deg: Float, paint: android.graphics.Paint) {
                val fm = paint.fontMetrics
                val baselineYOffset = - (fm.ascent + fm.descent) / 2f
                val halfW = paint.measureText(text) / 2f
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.rotate(deg + 270f, x, y)
                    nc.drawText(text, x - halfW, y + baselineYOffset, paint)
                    nc.restore()
                }
            }
            val startDeg = start
            val endDeg = start + span
            drawSign("–", sx, sy, startDeg, minusPaint)
            drawSign("+", ex, ey, endDeg, plusPaint)

            if (alertPrice != null) {
                val txt = euro(alertPrice!!)
                val txtPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.argb(200, 255, 122, 0)
                    textSize = 10.sp.toPx()
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }
                val arcRect = RectF(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)
                val textPath = AndroidPath().apply {
                    addArc(arcRect, start + span, -span)
                }
                val pm = android.graphics.PathMeasure(textPath, false)
                val textW = txtPaint.measureText(txt)
                val hOff = ((pm.length - textW) / 2f).coerceAtLeast(0f)
                val vOff = 8f
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawTextOnPath(txt, textPath, hOff, vOff, txtPaint)
                }
            }

            val rawTargetAng = start + (bottomBubbleStep + maxTicks) * stepAng
            val circumference = (2f * Math.PI.toFloat() * tubeR)
            val bubbleR = tubeWidth * 0.40f
            val bubbleArc = (bubbleR * 2f) / circumference * 360f
            val marginDeg = bubbleArc * 0.60f + 1.5f
            val clampedAng = rawTargetAng.coerceIn(start + marginDeg, start + span - marginDeg)

            val bRad = Math.toRadians(clampedAng.toDouble()).toFloat()
            val bx = cx + tubeR * cos(bRad)
            val by = cy + tubeR * sin(bRad)

            drawCircle(Color.White.copy(alpha = 0.28f), bubbleR, Offset(bx, by))
            drawCircle(Color.White.copy(alpha = 0.55f), bubbleR * 0.50f, Offset(bx + bubbleR * 0.35f, by - bubbleR * 0.35f))
            drawCircle(Color.Black.copy(alpha = 0.18f), bubbleR * 0.98f, Offset(bx, by + bubbleR * 0.20f), blendMode = BlendMode.Multiply)
        }

        // 6) GORNJA LIBELA + RSI
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val span = 50f
            val start = 270f - span / 2f
            val outer = radius * 0.920f
            val inner = radius * 0.860f

            val tubeR = (inner + outer) / 2f
            val tubeWidth = (outer - inner) * 1.80f
            val tubeRect = Rect(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)

            drawArc(
                color = Color(0xFF6F3511).copy(alpha = 0.45f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth * 0.30f, cap = StrokeCap.Round)
            )

            val startRad = Math.toRadians(start.toDouble()).toFloat()
            val endRad   = Math.toRadians((start + span).toDouble()).toFloat()
            val sx = cx + tubeR * cos(startRad)
            val sy = cy + tubeR * sin(startRad)
            val ex = cx + tubeR * cos(endRad)
            val ey = cy + tubeR * sin(endRad)

            val warmWhite = Color(0xFFDCD3C8)
            val signTextSize = 9.sp.toPx() * 1.10f
            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(
                    (warmWhite.alpha * 255).roundToInt(),
                    (warmWhite.red * 255).roundToInt(),
                    (warmWhite.green * 255).roundToInt(),
                    (warmWhite.blue * 255).roundToInt()
                )
                textSize = signTextSize
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            fun drawSign(text: String, x: Float, y: Float, deg: Float, paint: android.graphics.Paint) {
                val fm = paint.fontMetrics
                val baselineYOffset = -(fm.ascent + fm.descent) / 2f
                val halfW = paint.measureText(text) / 2f
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.rotate(deg + 90f, x, y)
                    nc.drawText(text, x - halfW, y + baselineYOffset, paint)
                    nc.restore()
                }
            }
            val startDeg = start
            val endDeg = start + span
            drawSign("0", sx, sy, startDeg, labelPaint)
            drawSign("100", ex, ey, endDeg, labelPaint)

            val rsiText = "RSI:${rsiAnimated.roundToInt()}"
            val txtPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(200, 255, 122, 0)
                textSize = 10.sp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }

            val arcRect = RectF(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)
            val textPath = AndroidPath().apply { addArc(arcRect, start, span) }
            val pm = android.graphics.PathMeasure(textPath, false)
            val textW = txtPaint.measureText(rsiText)
            val hOff = ((pm.length - textW) / 2f).coerceAtLeast(0f)
            val vOff = 30f

            drawIntoCanvas { c ->
                c.nativeCanvas.drawTextOnPath(rsiText, textPath, hOff, vOff, txtPaint)
            }

            val rsi01 = (rsiAnimated / 100f).coerceIn(0f, 1f)
            val rawAng = start + span * rsi01
            val circumference = (2f * Math.PI.toFloat() * tubeR)
            val bubbleR = tubeWidth * 0.40f
            val bubbleArc = (bubbleR * 2f) / circumference * 360f
            val marginDeg = bubbleArc * 0.60f + 1.5f
            val ang = rawAng.coerceIn(start + marginDeg, start + span - marginDeg)

            val bRad = Math.toRadians(ang.toDouble()).toFloat()
            val bx = cx + tubeR * cos(bRad)
            val by = cy + tubeR * sin(bRad)

            drawCircle(Color.White.copy(alpha = 0.28f), bubbleR, Offset(bx, by))
            drawCircle(Color.White.copy(alpha = 0.55f), bubbleR * 0.50f, Offset(bx + bubbleR * 0.35f, by - bubbleR * 0.35f))
            drawCircle(Color.Black.copy(alpha = 0.18f), bubbleR * 0.98f, Offset(bx, by + bubbleR * 0.20f), blendMode = BlendMode.Multiply)
        }

        // 7) mokri overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val levelY = (yWaterPxForDrawing / screenH) * h

            fun crestY(x: Float): Float {
                val ampBase = 18f * waveAmpMul
                val ampChop = 4.0f * waveAmpMul
                val lenLong = w / 1.35f
                val lenMid  = w / 0.95f
                val lenShort = w / 0.36f
                val phaseL = tAnim * 0.45f
                val phaseM = tAnim * 0.9f + 1.1f
                val phaseS = tAnim * 1.6f + 0.6f
                val twoPi = (Math.PI * 2).toFloat()
                return levelY +
                        ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                        (ampBase * 0.55f) * sin((x / lenMid) * twoPi + phaseM) * 0.35f +
                        ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f
            }

            val belowPath = Path().apply {
                moveTo(0f, crestY(0f)); lineTo(w, crestY(w)); lineTo(w, h); lineTo(0f, h); close()
            }
            clipPath(belowPath) {
                drawRect(Color(0xFFFF7A00).copy(alpha = 0.20f), blendMode = BlendMode.Multiply)
                val stripeH = 6f
                var y = levelY + 8f
                val gloss = Color.White.copy(alpha = 0.05f)
                while (y < h) {
                    drawRect(gloss, topLeft = Offset(0f, y), size = Size(w, 1.2f))
                    y += stripeH + 2f
                }
            }
        }

        // 8) desna libela (servisi)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val span = 50f
            val start = -span / 2f
            val stepAng = span / (maxTicks * 2)
            val outer = radius * 0.920f
            val inner = radius * 0.860f

            val tubeR = (inner + outer) / 2f
            val tubeWidth = (outer - inner) * 1.80f
            val tubeRect = Rect(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)

            drawArc(
                color = Color(0xFF6F3511).copy(alpha = 0.45f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = start, sweepAngle = span, useCenter = false,
                topLeft = tubeRect.topLeft, size = tubeRect.size,
                style = Stroke(width = tubeWidth * 0.30f, cap = StrokeCap.Round)
            )

            val startRad = Math.toRadians(start.toDouble()).toFloat()
            val endRad   = Math.toRadians((start + span).toDouble()).toFloat()
            val sx = cx + tubeR * cos(startRad)
            val sy = cy + tubeR * sin(startRad)
            val ex = cx + tubeR * cos(endRad)
            val ey = cy + tubeR * sin(endRad)


            val iconSize = (11.sp.toPx() * 1.25f).roundToInt()
            val srcTop = IntSize(iconTop.width, iconTop.height)
            val srcBot = IntSize(iconBottom.width, iconBottom.height)

            fun drawIcon(bitmap: ImageBitmap, srcSize: IntSize, x: Float, y: Float, deg: Float) {
                rotate(degrees = deg + 270f, pivot = Offset(x, y)) {
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = srcSize,
                        dstOffset = IntOffset((x - iconSize / 2f).roundToInt(), (y - iconSize / 2f).roundToInt()),
                        dstSize = IntSize(iconSize, iconSize),
                        alpha = 0.95f
                    )
                }
            }
            val startDeg = start
            val endDeg = start + span
            drawIcon(iconTop, srcTop, sx, sy, startDeg)
            drawIcon(iconBottom, srcBot, ex, ey, endDeg)

            val circumference = (2f * Math.PI.toFloat() * tubeR)
            val iconArc = iconSize / circumference * 360f
            val marginDeg = (iconArc * 0.6f) + 2f

            val rawTargetAng = start + (bubbleStep + maxTicks) * stepAng
            val clampedAng = rawTargetAng.coerceIn(start + marginDeg, start + span - marginDeg)

            val bRad = Math.toRadians(clampedAng.toDouble()).toFloat()
            val bx = cx + tubeR * cos(bRad)
            val by = cy + tubeR * sin(bRad)
            val bubbleR = tubeWidth * 0.40f
            drawCircle(Color.White.copy(alpha = 0.28f), bubbleR, Offset(bx, by))
            drawCircle(Color.White.copy(alpha = 0.55f), bubbleR * 0.50f, Offset(bx + bubbleR * 0.35f, by - bubbleR * 0.35f))
            drawCircle(Color.Black.copy(alpha = 0.18f), bubbleR * 0.98f, Offset(bx, by + bubbleR * 0.20f), blendMode = BlendMode.Multiply)

                // [IZMJENA] path-tekst "Req: N/800" — uvijek vidljiv (neovisno o servisu)
                run {
                    val reqText = "Req: ${reqUsedThisMonth}/${reqMonthlyQuota}"
                    val txtPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(200, 255, 122, 0)
                        textSize = 10.sp.toPx()
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD
                        )
                    }
                    val arcRect = RectF(cx - tubeR, cy - tubeR, cx + tubeR, cy + tubeR)
                    val textPath = AndroidPath().apply { addArc(arcRect, start, span) }
                    val pm = android.graphics.PathMeasure(textPath, false)
                    val textW = txtPaint.measureText(reqText)
                    val hOff = ((pm.length - textW) / 2f).coerceAtLeast(0f)
                    val vOff = 6f
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawTextOnPath(reqText, textPath, hOff, vOff, txtPaint)
                    }
                }

        }

        // === OVERLAY BADGE ispod SPOT-a (ne mijenja layout) ===
        if (!statusBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)   // centar ekrana (isti kao SPOT)
                    .offset(y = (-2).dp)      // fin pomak: -44.dp (više), -28.dp (niže)
                    .zIndex(2f)
            ) {
                StatusBadge(statusBadge!!)
            }
        }

        // TAP TARGET: donjih ~35% ekrana (otvara Alerts listu)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenAlerts() }) }
        )

        // TAP TARGET: desna libela (prebacuje servis)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.6f)
                .width(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onToggleService() }
                }
        )

        // TAP TARGET: gornjih ~30% ekrana (otvara Setup)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.30f)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenSetup() }) }
        )

        // POPUP – odabir alerta; spremamo kroz onSelectAlert
        if (showPicker) {
            AlertPickerDialog(
                alerts = alerts,
                spot = spotNow,
                onSelect = { chosen ->
                    alertPrice = chosen
                    onSelectAlert(chosen)
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}
/* ---------- FollowWaterText (ostaje tvoja verzija) ---------- */
@Composable
private fun FollowWaterText(
    id: String,
    text: String,
    txtColor: Color,
    fontSizeSp: Int,
    weight: FontWeight,
    t: Float,
    waterLevel: Float,
    yOffset: Dp = 0.dp,
    blurStrengthDp: Dp = 0.dp,
    modifier: Modifier = Modifier,
    followWave: Boolean = true,
    activeOverride: Float? = null
) {
    val density = LocalDensity.current

    var wet by remember(id) { mutableStateOf(0f) }
    var prevT by remember(id) { mutableStateOf(t) }
    var latched by remember(id) { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((fontSizeSp * 2.2f).dp)
    ) {
        fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val tt = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return tt * tt * (3f - 2f * tt)
        }

        val textSizePx = with(density) { fontSizeSp.sp.toPx() }
        val w = size.width
        val h = size.height

        val baseLine = h * 0.55f + with(density) { yOffset.toPx() }
        val twoPi = (Math.PI * 2).toFloat()

        val ampBase = 10f
        val ampChop = 2.6f
        val lenLong = w / 1.35f
        val lenMid  = w / 0.95f
        val lenShort = w / 0.36f
        val phaseL = t * 0.45f
        val phaseM = t * 0.9f + 1.1f
        val phaseS = t * 1.6f + 0.6f

        fun crestLocal(x: Float): Float =
            baseLine +
                    ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                    (ampBase * 0.55f) * sin((x / lenMid)  * twoPi + phaseM) * 0.35f +
                    ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

        val levelY = h * waterLevel + (sin(t * 0.35f) * 0.0045f) * h
        val wAmpBase = 18f
        val wAmpChop = 4.0f
        val wLenLong = w / 1.35f
        val wLenMid  = w / 0.95f
        val wLenShort = w / 0.36f
        fun crestWater(x: Float): Float =
            levelY +
                    wAmpBase * sin((x / wLenLong) * twoPi + phaseL) * 0.65f +
                    (wAmpBase * 0.55f) * sin((x / wLenMid) * twoPi + phaseM) * 0.35f +
                    wAmpChop * sin((x / wLenShort) * twoPi + phaseS) * 0.5f

        val measurePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = textSizePx
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                when (weight) {
                    FontWeight.SemiBold, FontWeight.Bold -> android.graphics.Typeface.BOLD
                    else -> android.graphics.Typeface.NORMAL
                }
            )
        }
        val textW = measurePaint.measureText(text)
        val cx = w / 2f

        val padPx = with(density) { 6.dp.toPx() }
        val left = max(0f, cx - textW / 2f - padPx)
        val right = min(w, cx + textW / 2f + padPx)

        val samples = 25
        var belowCnt = 0
        val submMarginPx = with(density) { 2.dp.toPx() }
        for (i in 0 until samples) {
            val x = left + (right - left) * (i / (samples - 1f))
            if (crestLocal(x) > crestWater(x) + submMarginPx) belowCnt++
        }
        val frac = belowCnt / samples.toFloat()
        latched = when {
            frac >= 0.30f -> true
            frac <= 0.15f -> false
            else -> latched
        }

        val goal = (activeOverride ?: if (latched) 1f else 0f).coerceIn(0f, 1f)
        val dt = (t - prevT).coerceIn(0f, 0.1f)
        prevT = t

        val tauIn = 0.35f
        val tauOut = 0.45f
        val tau = if (goal > wet) tauIn else tauOut
        val k = (1f - exp(-dt / max(1e-4f, tau))).coerceIn(0f, 1f)

        var newWet = wet + (goal - wet) * k

        val rateIn = 1.2f
        val rateOut = 0.8f
        val maxRate = if (goal > wet) rateIn else rateOut
        val maxDelta = maxRate * dt
        val delta = (newWet - wet).coerceIn(-maxDelta, maxDelta)
        newWet = wet + delta
        wet = newWet.coerceIn(0f, 1f)

        val blurPhase = smoothstep(0.15f, 0.55f, wet)
        val wavePhase = if (followWave) smoothstep(0.45f, 0.95f, wet) else 0f
        val darkPhase = smoothstep(0.30f, 1.00f, wet)

        val basePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.argb(
                (txtColor.alpha * 255).roundToInt(),
                (txtColor.red   * 255).roundToInt(),
                (txtColor.green * 255).roundToInt(),
                (txtColor.blue  * 255).roundToInt()
            )
            textSize = textSizePx
            typeface = measurePaint.typeface
        }
        val dim = 1f - 0.12f * darkPhase
        val blurMaxPx = with(density) { blurStrengthDp.toPx() }
        val blurPx = blurMaxPx * blurPhase

        fun makePaint(from: android.graphics.Paint, alphaMul: Float, blur: Float): android.graphics.Paint {
            return android.graphics.Paint(from).apply {
                alpha = (alpha * alphaMul).toInt().coerceIn(0, 255)
                maskFilter = if (blur > 0.5f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            }
        }

        val baselineAlpha = (1f - wavePhase) * dim
        val waveAlpha = wavePhase * dim

        val pBaseline = makePaint(basePaint, baselineAlpha, blurPx)
        val pWave     = makePaint(basePaint, waveAlpha, blurPx)

        val shadowBase = android.graphics.Paint(basePaint).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3.5f
            color = android.graphics.Color.argb(if (wet > 0.5f) 80 else 120, 0, 0, 0)
        }
        val sBaseline = makePaint(shadowBase, baselineAlpha, blurPx * 0.7f)
        val sWave     = makePaint(shadowBase, waveAlpha, blurPx * 0.7f)

        val fm = basePaint.fontMetrics
        val baselineYOffset = -(fm.ascent + fm.descent) / 2f

        if (baselineAlpha > 0.01f) {
            drawIntoCanvas {
                it.nativeCanvas.drawText(text, cx - textW / 2f, baseLine + baselineYOffset, sBaseline)
                it.nativeCanvas.drawText(text, cx - textW / 2f, baseLine + baselineYOffset, pBaseline)
            }
        }

        if (waveAlpha > 0.01f && followWave) {
            val path = android.graphics.Path().apply {
                moveTo(0f, crestLocal(0f))
                var x = 0f
                val step = 4f
                while (x <= w) {
                    lineTo(x, crestLocal(x))
                    x += step
                }
            }
            val pm = android.graphics.PathMeasure(path, false)
            val pathLen = pm.length
            val hOff = ((pathLen - textW) / 2f).coerceAtLeast(0f)
            drawIntoCanvas {
                it.nativeCanvas.drawTextOnPath(text, path, hOff, 0f, sWave)
                it.nativeCanvas.drawTextOnPath(text, path, hOff, 0f, pWave)
            }
        }
    }
}

/* ---------- POPUP: AlertPickerDialog ---------- */
@Composable
private fun AlertPickerDialog(
    alerts: List<Double>,
    spot: Double?,
    onSelect: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .background(Color(0xFF0B0B0B), RoundedCornerShape(16.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                WearText(
                    text = "Odaberi alert",
                    color = Color(0xFFFF7A00),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                )

                if (alerts.isEmpty()) {
                    WearText(
                        text = "Nema spremljenih alerta",
                        color = Color(0xFFB0B0B0),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 12.dp)
                    )
                } else {
                    alerts.forEach { price ->
                        val isBelow = spot?.let { price < it } ?: false
                        val color = if (isBelow) Color(0xFFE0524D) else Color(0xFF2FBF6B)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(price) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WearText(
                                text = "€" + String.format(Locale.US, "%,.2f", price),
                                color = color,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, shape = RoundedCornerShape(50))
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    WearText("Zatvori", color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

/* ---------- NOVO: StatusBadge ---------- */
@Composable
private fun StatusBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF333333), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        WearText(
            text = text,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}
