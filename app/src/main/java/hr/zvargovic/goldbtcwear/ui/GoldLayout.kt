package hr.zvargovic.goldbtcwear.ui

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlin.math.*
import java.util.Locale
import androidx.compose.runtime.withFrameNanos

// Android text-on-path
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.PathMeasure

// Animated WebP (bez Coil-a)
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.graphics.drawable.AnimatedImageDrawable
import androidx.core.content.ContextCompat
import hr.zvargovic.goldbtcwear.R

@Composable
fun GoldStaticScreen(modifier: Modifier = Modifier) {
    // --- Demo vrijednosti (zamijeni fetchom) ---
    val spotNow = 2315.40
    val premiumPct = 0.0049
    val buy = spotNow * (1 + premiumPct)
    val sell = spotNow * (1 - premiumPct)

    // Ref vrijednosti (placeholderi!)
    val dailyRef = 2310.00        // referenca za VODU (reset na početku dana)
    val lastRequestPrice = 2312.0 // referenca za SKALU (posljednji request)

    // Requests (placeholder)
    val usedRequests = 123
    val maxRequests  = 500

    // ---- Mapiranja ----
    // VODA: ±15% vs. dailyRef (0.5 = sredina)
    val deltaDaily = if (dailyRef > 0) (spotNow - dailyRef) / dailyRef else 0.0
    val levelT = (deltaDaily / 0.15).coerceIn(-1.0, 1.0) // -1..+1
    val waterLevel = 0.5 + 0.5 * levelT                  // 0..1

    // SKALA: ±0.5% vs. lastRequestPrice (0.1% po crtici, 11 crtica)
    val deltaHr = if (lastRequestPrice > 0) (spotNow - lastRequestPrice) / lastRequestPrice else 0.0
    val tick = 0.001     // 0.1%
    val maxTicks = 5     // ±5 → ±0.5%
    val rawSteps = (deltaHr / tick).roundToInt()
    val steps = rawSteps.coerceIn(-maxTicks, maxTicks)
    val overload = abs(deltaHr) > maxTicks * tick

    fun pctStr(p: Double): String {
        val sign = if (p >= 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", p * 100)}%"
    }
    fun euro(amount: Double): String = "€" + String.format(Locale.US, "%,.2f", amount)

    // anim clock (valovi) — mirnije
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

    // --- Blink animacija kad je overload (marker + label) ---
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // === 1) Kontejner s maskom i vodom (WebP ispod) ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()

                    val w = size.width
                    val h = size.height

                    val baseLevel = waterLevel.toFloat()
                    val levelY = h * baseLevel + (sin(t * 0.35f) * 0.0045f) * h

                    // sporiji, dulji valovi
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
                            colors = listOf(Color(0x6633AAFF), Color(0xFF001020)),
                            startY = levelY, endY = h
                        ),
                        blendMode = BlendMode.Multiply
                    )
                    val depthBrush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color(0x22000000),
                        1f to Color(0xAA000000)
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
                        listOf(Color(0xCC000000), Color(0x33000000), Color(0x33B2D6FF), Color(0x00B2D6FF)),
                        startY = levelY - feather, endY = levelY + feather
                    )
                    drawPath(crestPath, featherBrush, style = Stroke(width = feather, cap = StrokeCap.Butt))
                    drawPath(crestPath, Color(0x99FFFFFF), style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                    drawPath(crestPath, Color(0x6626B6FF), style = Stroke(width = 1.0f, cap = StrokeCap.Round))
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

        // === 2) Minutni krug + kružeća točka (1 krug = 1h) — unutra + DUPO veće točkice ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f
            val rBezel = radius * 0.965f     // unutra
            val dotR = 4.8f                  // duplo veće (prije 2.4f)
            val dotHi = 0.9f

            for (i in 0 until 60) {
                val ang = Math.toRadians((i * 6f - 90f).toDouble()).toFloat()
                val x = cx + rBezel * cos(ang)
                val y = cy + rBezel * sin(ang)
                drawCircle(Color(0x558FA3BA), dotR, Offset(x, y))
                drawCircle(Color(0x99AFC2D8), dotR * 0.55f, Offset(x + dotR*dotHi*0.3f, y - dotR*dotHi*0.35f))
            }
            val millis = System.currentTimeMillis()
            val hourFrac = ((millis % 3_600_000L).toFloat() / 3_600_000f)
            val ang = Math.toRadians((hourFrac * 360f - 90f).toDouble()).toFloat()
            val px = cx + rBezel * cos(ang); val py = cy + rBezel * sin(ang)
            drawCircle(Color(0xFF2A6AFF), 4.6f, Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.6f), 2.5f, Offset(px + 0.8f, py - 0.8f))
        }

        // === 3) Lijeva SKALA — JOŠ SKUPLJA, DUBLJE UNUTRA, DEBLJE CRTICE i MARKER, % NA 6. CRTICI ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val span = 50f
            val start = 180f - span / 2f
            val stepAng = span / (maxTicks * 2)

            val outer = radius * 0.920f
            val inner = radius * 0.860f

            // crtice
            for (i in -maxTicks..maxTicks) {
                val ang = Math.toRadians((start + (i + maxTicks) * stepAng).toDouble()).toFloat()
                val cosA = cos(ang); val sinA = sin(ang)
                val p1 = Offset(cx + inner * cosA, cy + inner * sinA)
                val p2 = Offset(cx + outer * cosA, cy + outer * sinA)
                val col = if (i == 0) Color(0xFFB0BAC8) else Color(0x668BA0B8)
                val sw = if (i == 0) 5.6f else 4.2f
                drawLine(col, p1, p2, strokeWidth = sw, cap = StrokeCap.Round)
            }

            // marker
            val markerSteps = steps
            val markerAng = start + (markerSteps + maxTicks) * stepAng
            val a = Math.toRadians(markerAng.toDouble()).toFloat()
            val mInner = inner - 3f
            val mOuter = outer + 6f
            val p1 = Offset(cx + mInner * cos(a), cy + mInner * sin(a))
            val p2 = Offset(cx + mOuter * cos(a), cy + mOuter * sin(a))
            val markerColor = when {
                markerSteps > 0 -> Color(0xFF1F6C35)
                markerSteps < 0 -> Color(0xFF7A231D)
                else -> Color(0xFFB0BAC8)
            }
            drawLine(
                markerColor.copy(alpha = markerAlpha),
                p1, p2,
                strokeWidth = 7.6f,
                cap = StrokeCap.Round
            )

            // natpis na zamišljenoj 6. crtici (−0.6%)
            val showPct = if (overload) deltaHr else markerSteps * tick.toDouble()
            val txt = pctStr(showPct)
            val txtPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = (if (showPct >= 0) 0xFF1F6C35 else 0xFF7A231D).toInt()
                textSize = 11.sp.toPx()
                alpha = (markerAlpha * 255).toInt()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
            }

            // računamo kut baš kao da crtamo crticu i = -6
            val extraStep = -7
            val txtAng = start + (extraStep + maxTicks) * stepAng
            val angRad = Math.toRadians(txtAng.toDouble()).toFloat()

// ↓ POVLAČENJE UNUTRA: promijeniš samo ovu vrijednost
            val rText = outer - 2f      // probaj -12f do -20f po ukusu
            val lx = cx + rText * cos(angRad)
            val ly = cy + rText * sin(angRad)

// rotiraj natpis da prati kut crtice, bez zrcaljenja
            drawIntoCanvas {
                it.save()
                it.rotate(txtAng +270f, lx, ly)         // +180f uklanja mirror
                it.nativeCanvas.drawText(
                    txt,
                    lx - txtPaint.measureText(txt) / 2f, // ispravno centriranje (prije je bilo /1f)
                    ly - 1.5f,                           // sitan lift baselinea (po želji)
                    txtPaint
                )
                it.restore()
            }
        }

        // === 4) Foreground: SPOT + BUY/SELL brojke ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gold (EUR/oz)", fontSize = 14.sp, color = Color(0xFFD0D7E6))
                Text(
                    euro(spotNow),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF2F4FA)
                )
            }
            Spacer(Modifier.height(36.dp))

            // umjesto Row -> Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal =12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp) // malo razmaka između BUY i SELL
            ) {
                Text(
                    euro(buy),
                    fontSize = 16.sp,
                    color = Color(0xFFF2F4FA),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    euro(sell),
                    fontSize = 16.sp,
                    color = Color(0xFFF2F4FA),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }

            Spacer(Modifier.weight(1f))
        }

        // === 5) Requests po DNU — tekst po luku: "Requests 123/500" ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val radius = min(w, h) / 2f

            val arcText = "Requests  $usedRequests/$maxRequests"
            val textSizePx = 11.sp.toPx()
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(220, 202, 212, 228) // #CAD4E4 ~ 0.86 alpha
                textSize = textSizePx
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL
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

        // === 6) "MOKRI" EFEKT NA POTOPLJENOM TEKSTU ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val levelY = h * waterLevel.toFloat() + (sin(t * 0.35f) * 0.0045f) * h

            val ampBase = 18f
            val ampChop = 4.0f
            val lenLong = w / 1.35f
            val lenMid  = w / 0.95f
            val lenShort = w / 0.36f
            val phaseL = t * 0.45f
            val phaseM = t * 0.9f + 1.1f
            val phaseS = t * 1.6f + 0.6f

            fun crestY(x: Float): Float =
                levelY +
                        ampBase * sin((x / lenLong) * (Math.PI*2).toFloat() + phaseL) * 0.65f +
                        (ampBase * 0.55f) * sin((x / lenMid) * (Math.PI*2).toFloat() + phaseM) * 0.35f +
                        ampChop * sin((x / lenShort) * (Math.PI*2).toFloat() + phaseS) * 0.5f

            val belowPath = Path().apply {
                moveTo(0f, crestY(0f)); lineTo(w, crestY(w)); lineTo(w, h); lineTo(0f, h); close()
            }
            clipPath(belowPath) {
                drawRect(Color(0x3326B6FF), blendMode = BlendMode.Multiply)
                val stripeH = 6f
                var y = levelY + 8f
                val gloss = Color.White.copy(alpha = 0.06f)
                while (y < h) {
                    drawRect(gloss, topLeft = Offset(0f, y), size = Size(w, 1.2f))
                    y += stripeH + 2f
                }
            }
        }
    }
}