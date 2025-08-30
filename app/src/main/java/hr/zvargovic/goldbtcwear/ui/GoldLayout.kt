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

// Text-on-path (Android)
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.PathMeasure

// Animated WebP bez Coil-a
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

    // clock za blagi “bob”
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
        // === 1) KONTEJNER S MASKOM (unutra je AndroidView) ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // nacrtaj sve dijete (AndroidView ispod)
                    drawContent()

                    val w = size.width
                    val h = size.height

                    // razina vode (diže se s progress) + blagi bob
                    val baseLevel = 0.55f + 0.35f * progress
                    val bob = (sin(t * 0.6f) * 0.008f)
                    val levelY = h * (baseLevel + bob)

                    // valovita površina
                    val amp = 6f
                    val len1 = w / 1.4f
                    val len2 = w / 0.9f
                    val phase1 = t * 1.1f
                    val phase2 = t * 2.1f + 1.6f
                    val k = (Math.PI * 2).toFloat()

                    // GORNJI poligon (iznad vala) – izrezujemo ga iz destinacije
                    val cutTop = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(w, levelY)
                        var x = w
                        val step = 5f
                        while (x >= 0f) {
                            val y = levelY +
                                    amp * sin((x / len1) * k + phase1) * 0.7f +
                                    (amp * 0.6f) * sin((x / len2) * k + phase2) * 0.3f
                            lineTo(x, y); x -= step
                        }
                        lineTo(0f, levelY)
                        close()
                    }

                    // DstOut → sve iznad vala “odreži”; WebP ostaje samo ispod
                    drawPath(cutTop, Color.White, blendMode = BlendMode.DstOut)
                }
        ) {
            // Ovo je SAD dio drawContent-a gore — znači maska ga reže
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

        // === 2) HIGHLIGHT po rubu površine (iznad maske) ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseLevel = 0.55f + 0.35f * progress
            val bob = (sin(t * 0.6f) * 0.008f)
            val levelY = h * (baseLevel + bob)

            val amp = 6f
            val len1 = w / 1.4f
            val len2 = w / 0.9f
            val phase1 = t * 1.1f
            val phase2 = t * 2.1f + 1.6f
            val k = (Math.PI * 2).toFloat()

            val crestPath = Path().apply {
                moveTo(0f, levelY)
                var x = 0f
                val step = 6f
                while (x <= w) {
                    val y = levelY +
                            amp * sin((x / len1) * k + phase1) * 0.7f +
                            (amp * 0.6f) * sin((x / len2) * k + phase2) * 0.3f
                    lineTo(x, y); x += step
                }
            }
            drawPath(crestPath, Color(0x77FFFFFF), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            drawPath(crestPath, Color(0x5526B6FF), style = Stroke(width = 1.0f, cap = StrokeCap.Round))
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

            // request arc (dno)
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