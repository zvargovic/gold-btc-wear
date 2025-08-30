package hr.zvargovic.goldbtcwear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlin.math.*
import java.util.Locale
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.nativeCanvas

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
    // --- Demo podaci ---
    val spot = 2315.40
    val premiumPct = 0.0049
    val buy = spot * (1 + premiumPct)
    val sell = spot * (1 - premiumPct)

    val usedRequests = 123
    val maxRequests = 500
    val progress = (usedRequests.toFloat() / maxRequests.toFloat()).coerceIn(0f, 1f)

    fun euro(amount: Double): String = "€" + String.format(Locale.US, "%,.2f", amount)

    // anim clock
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last == 0L) last = now
            val dt = (now - last) / 1_000_000_000f
            last = now
            t += dt
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // === 1) Kontejner s maskom (unutra je WebP) ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()

                    val w = size.width
                    val h = size.height

                    // razina vode (55%..90%) + blagi bob
                    val baseLevel = 0.55f + 0.35f * progress
                    val bob = (sin(t * 0.6f) * 0.008f)
                    val levelY = h * (baseLevel + bob)

                    // --- val parametri (pojačani) ---
                    val ampBase = 20f            // veći valovi
                    val ampChop = 5.5f          // kratki “choppy” valovi
                    val lenLong = w / 1.2f
                    val lenMid  = w / 0.8f
                    val lenShort = w / 0.28f    // kratka valna duljina za prskanje
                    val phaseL = t * 0.9f
                    val phaseM = t * 1.9f + 1.2f
                    val phaseS = t * 3.6f + 0.7f
                    val twoPi = (Math.PI * 2).toFloat()

                    fun crestY(x: Float): Float =
                        levelY +
                                ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                                (ampBase * 0.55f) * sin((x / lenMid)  * twoPi + phaseM) * 0.35f +
                                ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

                    // GORNJI poligon (izrezujemo sve iznad vala)
                    val cutTop = Path().apply {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, crestY(w))
                        var x = w
                        val step = 4f
                        while (x >= 0f) {
                            lineTo(x, crestY(x))
                            x -= step
                        }
                        lineTo(0f, crestY(0f))
                        close()
                    }
                    drawPath(cutTop, Color.White, blendMode = BlendMode.DstOut)

                    // === WATER SHADING (1 + 2 kombinacija) ===
                    // 1) osnovni vodeni gradijent ispod vala
                    val waterPath = Path().apply {
                        moveTo(0f, crestY(0f))
                        lineTo(w, crestY(w))
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(
                        path = waterPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x6633AAFF),  // svjetlije pri površini
                                Color(0xFF001020)   // tamno prema dnu
                            ),
                            startY = levelY,
                            endY = h
                        ),
                        blendMode = BlendMode.Multiply
                    )
                    // 2) dodatni depth overlay (još tamnije dno)
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

        // === 2) Prijelaz + pjena + spray ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val baseLevel = 0.55f + 0.35f * progress
            val bob = (sin(t * 0.6f) * 0.008f)
            val levelY = h * (baseLevel + bob)

            // isti parametri kao gore
            val ampBase = 9f
            val ampChop = 3.5f
            val lenLong = w / 1.2f
            val lenMid  = w / 0.8f
            val lenShort = w / 0.28f
            val phaseL = t * 0.9f
            val phaseM = t * 1.9f + 1.2f
            val phaseS = t * 3.6f + 0.7f
            val twoPi = (Math.PI * 2).toFloat()

            fun crestY(x: Float): Float =
                levelY +
                        ampBase * sin((x / lenLong) * twoPi + phaseL) * 0.65f +
                        (ampBase * 0.55f) * sin((x / lenMid)  * twoPi + phaseM) * 0.35f +
                        ampChop * sin((x / lenShort) * twoPi + phaseS) * 0.5f

            // putanja grebena
            val crestPath = Path().apply {
                moveTo(0f, crestY(0f))
                var x = 0f
                val step = 4f
                while (x <= w) {
                    lineTo(x, crestY(x))
                    x += step
                }
            }

            // Feathered blend — deblji za “zapljuskivanje”
            val feather = 30f
            val featherBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xCC000000),
                    Color(0x33000000),
                    Color(0x33B2D6FF),
                    Color(0x00B2D6FF)
                ),
                startY = levelY - feather,
                endY = levelY + feather
            )
            drawPath(crestPath, featherBrush, style = Stroke(width = feather, cap = StrokeCap.Butt))

            // svijetli highlight na samom grebenu
            drawPath(crestPath, Color(0x99FFFFFF), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            drawPath(crestPath, Color(0x6626B6FF), style = Stroke(width = 1.1f, cap = StrokeCap.Round))

            // gušća pjena uz greben
            val rndSeed = (t * 90f).toInt()
            var x = 0f
            val foamStep = 57f // gušće
            while (x <= w) {
                val y = crestY(x)
                val jitterX = ((x.toInt() + rndSeed) % 7 - 3) * 0.5f
                val jitterY = ((x.toInt() - rndSeed) % 5 - 2) * 0.4f
                val r = 0.9f + ((x.toInt() + rndSeed) % 3) * 0.45f
                drawCircle(Color.White.copy(alpha = 0.60f), r, Offset(x + jitterX, y - 1.8f + jitterY))
                x += foamStep
            }

            // sitni “spray” iznad grebena (random kapljice malo iznad)
            var sx = 0f
            val sprayStep = 18f
            while (sx <= w) {
                val y = crestY(sx)
                // par kapljica iznad
                val up = 6f + ((sx.toInt() + rndSeed) % 8)
                val count = 2 + ((sx.toInt() / 30 + rndSeed) % 2)
                repeat(count) { i ->
                    val ox = ((i * 7 + rndSeed) % 11 - 5) * 0.7f
                    val oy = up + ((i + rndSeed) % 5) * 0.8f
                    val rr = 0.7f + ((i + rndSeed) % 3) * 0.3f
                    drawCircle(Color.White.copy(alpha = 0.35f), rr, Offset(sx + ox, y - oy))
                }
                sx += sprayStep
            }
        }

        // === 3) UI: bezel markeri + request arc + bottom text ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) / 2f * 0.92f

            // minute bezel (60 tiny bubbles)
            val markers = 60
            val bezelColorMain = Color(0x55B4D2FF)
            val bezelColorHi = Color(0x88B4D2FF)
            val rOuter = radius
            val rInner = radius * 0.965f
            val bubbleR = 2.2f
            for (i in 0 until markers) {
                val ang = Math.toRadians((i / 60f) * 360.0 - 90.0).toFloat()
                val rx = (rOuter + rInner) / 2f
                val x = cx + rx * cos(ang)
                val y = cy + rx * sin(ang)
                drawCircle(bezelColorMain, bubbleR, Offset(x, y))
                drawCircle(bezelColorHi, bubbleR * 0.55f, Offset(x + 0.35f * bubbleR, y - 0.45f * bubbleR))
            }

            // request arc
            val arcStroke = 12f
            val inset = arcStroke / 2 + 10f
            val arcRect = Rect(
                left = cx - radius + inset,
                top = cy - radius + inset,
                right = cx + radius - inset,
                bottom = cy + radius - inset
            )
            val arcBlue = Color(0xFF2A6AFF)
            val start = 20f
            val sweep = 140f
            drawArc(arcBlue.copy(alpha = 0.18f), start, sweep, false, arcRect.topLeft, arcRect.size,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round))
            drawArc(arcBlue, start, sweep * progress, false, arcRect.topLeft, arcRect.size,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round))

            // bottom arc text
            val arcText = "Requests  $usedRequests / $maxRequests"
            val textSizePx = 13.sp.toPx()
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = textSizePx
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.NORMAL
                )
            }
            val textRadius = radius * 0.955f
            val oval = RectF(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
            val path = AndroidPath().apply { addArc(oval, 160f, -140f) }
            val pathLen = PathMeasure(path, false).length
            val textLen = p.measureText(arcText)
            val hOff = ((pathLen - textLen) / 2f).coerceAtLeast(0f)
            val vOff = -1.5f
            drawIntoCanvas { it.nativeCanvas.drawTextOnPath(arcText, path, hOff, vOff, p) }
        }

        // === 4) FOREGROUND: naslov, cijena, BUY/SELL ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gold (EUR/oz)", fontSize = 14.sp, color = Color(0xFFD0D7E6))
                Text(euro(spot), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2F4FA))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareSwatch(Color(0xFF1F6C35)); Spacer(Modifier.width(10.dp))
                Text("BUY", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(18.dp))
                Text("SELL", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                SquareSwatch(Color(0xFF7A231D))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(euro(buy), fontSize = 16.sp, color = Color(0xFFF2F4FA),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
                Text(euro(sell), fontSize = 16.sp, color = Color(0xFFF2F4FA),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SquareSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}