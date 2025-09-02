package hr.zvargovic.goldbtcwear.ui

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.wear.compose.material.Text
import kotlin.math.*
import java.util.Locale
import androidx.compose.runtime.withFrameNanos

// Android text-on-path & blur
import android.graphics.BlurMaskFilter
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.PathMeasure

// Animated WebP (bez Coil-a)
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.graphics.drawable.AnimatedImageDrawable
import androidx.core.content.ContextCompat
import hr.zvargovic.goldbtcwear.R

enum class PriceService { TwelveData, Yahoo }

@Composable
fun GoldStaticScreen(modifier: Modifier = Modifier) {
    // --- Demo vrijednosti ---
    val spotNow = 2315.40
    val premiumPct = 0.0049
    val buy = spotNow * (1 + premiumPct)
    val sell = spotNow * (1 - premiumPct)

    val lastRequestPrice = 2312.0

    // Requests (placeholder)
    val usedRequests = 123
    val maxRequests  = 500

    // --- VODA: veži za sekunde unutar minute (glatko 0→1) ---
    val fracSec by produceState(initialValue = 0f) {
        var _last = 0L
        while (true) withFrameNanos { now ->
            val ms = System.currentTimeMillis() % 60_000L
            value = ms / 60_000f
            _last = now
        }
    }
    // invertirano: kako vrijeme teče, razina vode raste (levelY ide prema vrhu)
    val waterLevel = 0.90f - (0.90f - 0.15f) * fracSec

    // --- Ostalo (tickovi, overload) ---
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

    // anim clock (valovi)
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last == 0L) last = now
            val dt = (now - last) / 1_000_000_000f
            last = now
            t += dt * 0.30f
        }
    }

    // Blink za overload
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )
    val markerAlpha = if (overload) blinkAlpha else 1f

    // --- Orange theme boje ---
    val orangeDim   = Color(0x66FF7A00)
    val orangeLine  = Color(0xFFFF7A00)
    val orangeLite  = Color(0xFFFFA040)
    val warmWhite   = Color(0xFFDCD3C8)

    // BUY/SELL + marker
    val buyTint  = Color(0xFF2FBF6B)
    val sellTint = Color(0xFFE0524D)

    // Aktivni servis (placeholder)
    var activeService by remember { mutableStateOf(PriceService.TwelveData) }

    // Animirani cilj mjehurića libele
    val bubbleStep by animateFloatAsState(
        targetValue = if (activeService == PriceService.TwelveData) 5f else -5f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bubbleStep"
    )

    // Ikone (PNG u drawable)
    val res = LocalContext.current.resources
    val iconTop = remember { ImageBitmap.imageResource(res, R.drawable.ic_twelve) }   // vrh luka
    val iconBottom = remember { ImageBitmap.imageResource(res, R.drawable.ic_yahoo) } // dno luka

    // --- Apsolutna razina vode u pikselima (0 = vrh ekrana) ---
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val yWaterPx = screenHeightPx * waterLevel + (sin(t * 0.35f) * 0.0045f) * screenHeightPx

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Debug overlay (gore desno)
        Text(
            text = "yWater = %.1f px (lvl=%.3f)".format(yWaterPx, waterLevel),
            color = Color.Cyan,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )

        // === 1) Kontejner s maskom i vodom (WebP ispod) ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()

                    val w = size.width
                    val h = size.height

                    val levelY = h * waterLevel + (sin(t * 0.35f) * 0.0045f) * h

                    // valovi
                    val ampBase = 18f
                    val ampChop = 4.0f
                    val lenLong = w / 1.35f
                    val lenMid  = w / 0.95f
                    val lenShort = w / 0.36f
                    val phaseL = t * 0.45f
                    val phaseM = t * 0.9f + 1.1f
                    val phaseS = t * 1.6f + 0.6f
                    val twoPi = (Math.PI * 2).toFloat()

                    fun crestY(x: Float): Float =
                        levelY +
                                ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                                (ampBase * 0.55f) * sin((x / lenMid)  * twoPi + phaseM) * 0.35f +
                                ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

                    // maska iznad vala
                    val cutTop = Path().apply {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, crestY(w))
                        var x = w; val step = 4f
                        while (x >= 0f) { lineTo(x, crestY(x)); x -= step }
                        lineTo(0f, crestY(0f)); close()
                    }
                    drawPath(cutTop, Color.White, blendMode = BlendMode.DstOut)

                    // voda
                    val waterPath = Path().apply {
                        moveTo(0f, crestY(0f)); lineTo(w, crestY(w)); lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(
                        path = waterPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x66FF7A00), Color(0xFF1A0A00)),
                            startY = levelY, endY = h
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

                    // rub vala
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

        // === 1b) Suptilni mjehurići ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val levelY = h * waterLevel + (sin(t * 0.35f) * 0.0045f) * h

            val ampBase = 18f
            val ampChop = 4.0f
            val lenLong = w / 1.35f
            val lenMid  = w / 0.95f
            val lenShort = w / 0.36f
            val phaseL = t * 0.45f
            val phaseM = t * 0.9f + 1.1f
            val phaseS = t * 1.6f + 0.6f
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
            val tNorm = (t / life)

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
                    val spotPx = 30.sp.toPx()
                    fun flerp(a: Float, b: Float, t: Float) = a + (b - a) * t
                    val maxR = spotPx * 0.5f
                    val minR = maxR * 0.25f
                    val baseR = flerp(minR, maxR, seed)
                    val r = (baseR * (0.85f + 0.35f * e)).coerceAtMost(maxR)
                    val a = (0.18f * (1f - frac)) * 0.85f
                    val sway = (sin((t + seed * 7f) * 0.9f) * 2.0f)

                    drawCircle(Color.White.copy(alpha = a * 0.75f), r, Offset(x + sway, y))
                    drawCircle(Color.White.copy(alpha = a), r * 0.55f, Offset(x + sway + r * 0.35f, y - r * 0.35f))

                    if (frac > 0.65f) {
                        val k = ((frac - 0.65f) / 0.35f).coerceIn(0f, 1f)
                        val dots = 3
                        for (d in 0 until dots) {
                            val ang = twoPi * (seed * 13f + d * 0.33f)
                            val rr = r * (1.6f + 0.6f * d)
                            val ox = cos(ang) * rr * k
                            val oy = sin(ang) * rr * k
                            drawCircle(
                                Color.White.copy(alpha = a * (0.5f * (1f - k))),
                                r * (0.18f + 0.10f * d),
                                Offset(x + sway + ox, y - 2f + oy)
                            )
                        }
                    }
                }
                x += stepX
            }
        }

        // === 2) Minutni krug ===
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
                drawCircle(Color(0x88FFC07A), dotR * 0.55f, Offset(x + dotR*dotHi*0.3f, y - dotR*dotHi*0.35f))
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

        // === 3) Lijeva SKALA — kompaktna ===
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
            val baselineYOffset = - (fm.ascent + fm.descent) / 2f
            val halfTextW = txtPaint.measureText(txt) / 2f
            drawIntoCanvas { c ->
                c.save()
                c.translate(cxT, cyT)
                c.rotate(txtAng + 270f)
                c.nativeCanvas.drawText(txt, -halfTextW, baselineYOffset, txtPaint)
                c.restore()
            }
        }

        // === 4) Foreground: SVE CIJENE I NASLOV — realistični prijelazi ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))

            // --- Naslov + SPOT ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Gold/oz  → blur + wave kad je yWater ≤ 95 px
                FollowWaterText(
                    id = "title",
                    text = "Gold (EUR/oz)",
                    txtColor = orangeLine,
                    fontSizeSp = 14,
                    weight = FontWeight.Normal,
                    t = t,
                    waterLevel = waterLevel,
                    yOffset = 0.dp,
                    blurStrengthDp = 1.5.dp,
                    followWave = true,
                    activeOverride = if (yWaterPx <= 95f) 1f else 0f
                )

                // Spot  → samo blur kad je yWater ≤ 150 px (fiksan Y)
                FollowWaterText(
                    id = "spot",
                    text = euro(spotNow),
                    txtColor = orangeLine,
                    fontSizeSp = 30,
                    weight = FontWeight.Bold,
                    t = t,
                    waterLevel = waterLevel,
                    yOffset = (-20).dp,
                    blurStrengthDp = 2.dp,
                    followWave = false,
                    activeOverride = if (yWaterPx <= 150f) 1f else 0f
                )
            }

            Spacer(Modifier.height(22.dp))

            // --- BUY / SELL ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Buy  → blur + wave kad je yWater ≤ 265 px
                FollowWaterText(
                    id = "buy",
                    text = euro(buy),
                    txtColor = androidx.compose.ui.graphics.lerp(orangeLine, buyTint, 0.65f),
                    fontSizeSp = 18,
                    weight = FontWeight.SemiBold,
                    t = t,
                    waterLevel = waterLevel,
                    yOffset = (-42).dp,
                    blurStrengthDp = 2.dp,
                    followWave = true,
                    activeOverride = if (yWaterPx <= 265f) 1f else 0f
                )

                // Sell → blur + wave kad je yWater ≤ 300 px
                FollowWaterText(
                    id = "sell",
                    text = euro(sell),
                    txtColor = androidx.compose.ui.graphics.lerp(orangeLine, sellTint, 0.65f),
                    fontSizeSp = 18,
                    weight = FontWeight.SemiBold,
                    t = t,
                    waterLevel = waterLevel,
                    yOffset = (-42).dp,
                    blurStrengthDp = 2.dp,
                    followWave = true,
                    activeOverride = if (yWaterPx <= 300f) 1f else 0f
                )
            }

            Spacer(Modifier.weight(1f))
        }

        // === 5) Requests po dnu ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val arcText = "Requests  $usedRequests/$maxRequests"
            val textSizePx = 11.sp.toPx()
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(230, 255, 210, 170)
                textSize = textSizePx
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.NORMAL
                )
            }
            val textRadius = radius * 0.86f
            val oval = RectF(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
            val path = AndroidPath().apply { addArc(oval, 160f, -140f) }
            val pathLen = PathMeasure(path, false).length
            val textLen = p.measureText(arcText)
            val hOff = ((pathLen - textLen) / 2f).coerceAtLeast(0f)
            drawIntoCanvas { it.nativeCanvas.drawTextOnPath(arcText, path, hOff, 0f, p) }
        }

        // === 6) “MOKRI” overlay pod vodom ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val levelY = h * waterLevel + (sin(t * 0.35f) * 0.0045f) * h

            fun crestY(x: Float): Float {
                val ampBase = 18f
                val ampChop = 4.0f
                val lenLong = w / 1.35f
                val lenMid  = w / 0.95f
                val lenShort = w / 0.36f
                val phaseL = t * 0.45f
                val phaseM = t * 0.9f + 1.1f
                val phaseS = t * 1.6f + 0.6f
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

        // === 7) LIBELA — desna, 2× deblja, ikone + clamp ===
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
                startAngle = start,
                sweepAngle = span,
                useCenter = false,
                topLeft = tubeRect.topLeft,
                size = tubeRect.size,
                style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = start,
                sweepAngle = span,
                useCenter = false,
                topLeft = tubeRect.topLeft,
                size = tubeRect.size,
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
        }
    }
}

/* ---------- FollowWaterText ----------
 * Realističan “mokri” prijelaz po elementu:
 * - Goal (0/1) dolazi kroz activeOverride (pragovi po px) ili iz lokalne potopljenosti
 * - EMA smoothing s vremenskom konstantom + ograničenje brzine po sekundi
 * - Fazno paljenje: blur od ranije, wave kasnije
 * - Crossfade baseline ↔ wave (bez skoka)
 */
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
    followWave: Boolean = true,         // default “plivaj”
    activeOverride: Float? = null       // px-based override (0..1)
) {
    val density = LocalDensity.current

    // Stanja za glatki prijelaz
    var wet by remember(id) { mutableStateOf(0f) }      // 0=dry, 1=wet
    var prevT by remember(id) { mutableStateOf(t) }     // za dt
    var latched by remember(id) { mutableStateOf(false) }  // fallback lokalna histereza

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

        // Lokalna krivulja (mirnija za čitljivost)
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

        // Voda (ista formula kao gore)
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

        // 1) Širina teksta
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

        // 2) Lokalna potopljenost (fallback kad nema override)
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

        // 3) EMA + rate limit prema cilju (override ili lokalno)
        val goal = (activeOverride ?: if (latched) 1f else 0f).coerceIn(0f, 1f)
        val dt = (t - prevT).coerceIn(0f, 0.1f) // s
        prevT = t

        // vremenska konstanta (sek) — koliko brzo “natapa”
        val tauIn = 0.35f
        val tauOut = 0.45f
        val tau = if (goal > wet) tauIn else tauOut
        val k = (1f - exp(-dt / max(1e-4f, tau))).coerceIn(0f, 1f)

        var newWet = wet + (goal - wet) * k

        // ograničenje brzine promjene (per-second rate limit)
        val rateIn = 1.2f   // brže moči
        val rateOut = 0.8f  // malo sporije suši
        val maxRate = if (goal > wet) rateIn else rateOut
        val maxDelta = maxRate * dt
        val delta = (newWet - wet).coerceIn(-maxDelta, maxDelta)
        newWet = wet + delta
        wet = newWet.coerceIn(0f, 1f)

        // 4) Fazno paljenje efekata
        val blurPhase = smoothstep(0.15f, 0.55f, wet)             // blur se pali ranije
        val wavePhase = if (followWave) smoothstep(0.45f, 0.95f, wet) else 0f // val kasnije
        val darkPhase = smoothstep(0.30f, 1.00f, wet)             // blago zatamnjenje

        // 5) Paintovi (dinamički blur + dim)
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

        // ukupna “osnovna” alfa nakon zatamnjenja
        val baseAlphaMul = dim

        // baseline (ravni tekst) i wave (na putanji) – crossfade
        val baselineAlpha = (1f - wavePhase) * baseAlphaMul
        val waveAlpha = wavePhase * baseAlphaMul

        val pBaseline = makePaint(basePaint, baselineAlpha, blurPx)
        val pWave     = makePaint(basePaint, waveAlpha, blurPx)

        val shadowBase = android.graphics.Paint(basePaint).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3.5f
            color = android.graphics.Color.argb(if (wet > 0.5f) 80 else 120, 0, 0, 0)
        }
        val sBaseline = makePaint(shadowBase, baselineAlpha, blurPx * 0.7f)
        val sWave     = makePaint(shadowBase, waveAlpha, blurPx * 0.7f)

        // 6) Crtanje: baseline ↔ wave crossfade (bez skoka)
        val fm = basePaint.fontMetrics
        val baselineYOffset = -(fm.ascent + fm.descent) / 2f

        // baseline (ravno)
        if (baselineAlpha > 0.01f) {
            drawIntoCanvas {
                it.nativeCanvas.drawText(text, cx - textW / 2f, baseLine + baselineYOffset, sBaseline)
                it.nativeCanvas.drawText(text, cx - textW / 2f, baseLine + baselineYOffset, pBaseline)
            }
        }

        // wave (na putanji)
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
            val pm = PathMeasure(path, false)
            val pathLen = pm.length
            val hOff = ((pathLen - textW) / 2f).coerceAtLeast(0f)
            drawIntoCanvas {
                it.nativeCanvas.drawTextOnPath(text, path, hOff, 0f, sWave)
                it.nativeCanvas.drawTextOnPath(text, path, hOff, 0f, pWave)
            }
        }
    }
}

/** (Rezervni “tinted” tekst ako zatreba bez valova.) */
@Composable
private fun TintedPrice(
    text: String,
    baseColor: Color,
    tint: Color,
    tintAlpha: Float,
    fontSizeSp: Int,
    weight: FontWeight
) {
    Box(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(tint.copy(alpha = tintAlpha), blendMode = BlendMode.SrcAtop)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSizeSp.sp,
            color = baseColor,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}