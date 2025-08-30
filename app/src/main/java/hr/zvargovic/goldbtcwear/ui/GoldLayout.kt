package hr.zvargovic.goldbtcwear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlin.math.*
import java.util.Locale
import androidx.compose.runtime.withFrameNanos

// Android helpers for text-on-path
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.PathMeasure

@Composable
fun GoldStaticScreen(modifier: Modifier = Modifier) {
    // ---- Static demo data ----
    val spot = 2315.40
    val premiumPct = 0.0049
    val buy = spot * (1 + premiumPct)
    val sell = spot * (1 - premiumPct)

    val usedRequests = 123
    val maxRequests = 500
    val progress = (usedRequests.toFloat() / maxRequests.toFloat()).coerceIn(0f, 1f)

    fun euro(amount: Double): String {
        val us = String.format(Locale.US, "%,.2f", amount)
        return "€$us"
    }

    // ---- Animation clock ----
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = (now - last) / 1_000_000_000f
                last = now
                t += dt
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) / 2f * 0.92f

            // --- MINUTE BEZEL: 60 tiny bubbles ---
            val markers = 60
            val bezelColorMain = Color(0x55B4D2FF)
            val bezelColorHi   = Color(0x88B4D2FF)
            val bezelRadiusOuter = radius
            val bezelRadiusInner = radius * 0.965f
            val bubbleR = 2.2f
            for (i in 0 until markers) {
                val ang = Math.toRadians((i / 60f) * 360.0 - 90.0).toFloat()
                val rx = (bezelRadiusOuter + bezelRadiusInner) / 2f
                val x = cx + rx * cos(ang)
                val y = cy + rx * sin(ang)
                drawCircle(color = bezelColorMain, radius = bubbleR, center = Offset(x, y))
                drawCircle(
                    color = bezelColorHi,
                    radius = bubbleR * 0.55f,
                    center = Offset(x + 0.35f * bubbleR, y - 0.45f * bubbleR)
                )
            }

            // --- REQUEST ARC (BOTTOM) ---
            val arcStroke = 12f
            val arcInset = arcStroke / 2 + 10f
            val arcRect = Rect(
                left = cx - radius + arcInset,
                top = cy - radius + arcInset,
                right = cx + radius - arcInset,
                bottom = cy + radius - arcInset
            )
            val arcBlue = Color(0xFF2A6AFF)
            val startAngleBottom = 20f
            val sweepAngleBottom = 140f
            drawArc(
                color = arcBlue.copy(alpha = 0.18f),
                startAngle = startAngleBottom,
                sweepAngle = sweepAngleBottom,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcBlue,
                startAngle = startAngleBottom,
                sweepAngle = sweepAngleBottom * progress,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )

            // --- WATER (animated base: 3 waves) ---
            val baseLevel = 0.58f + 0.30f * progress
            val bob = (sin(t * 0.6f) * 0.008f)
            val waterTop = h * (baseLevel + bob)

            fun wavePath(top: Float, amp: Float, len: Float, phase: Float, step: Float): Path {
                return Path().apply {
                    moveTo(0f, top)
                    var x = 0f
                    val k = (Math.PI * 2).toFloat()
                    while (x <= w) {
                        val y = top + amp * sin((x / len) * k + phase)
                        lineTo(x, y); x += step
                    }
                    lineTo(w, h); lineTo(0f, h); close()
                }
            }

            // Base layers
            val wave1 = wavePath(waterTop, 12f, w / 1.7f, t * 0.7f, 6f)
            val wave2Top = waterTop - 6f
            val wave2Phase = t * 1.2f + 0.8f
            val wave2 = wavePath(wave2Top, 7f, w / 1.2f, wave2Phase, 4.5f)
            val wave3Top = waterTop - 3.5f
            val wave3 = wavePath(wave3Top, 3.2f, w / 0.9f, t * 2.1f + 1.6f, 3.5f)

            drawPath(
                path = wave1,
                brush = Brush.verticalGradient(
                    0f to Color(0xFF0E2E54),
                    0.6f to Color(0xFF0C2646),
                    1f to Color(0xFF091C34)
                ),
                alpha = 0.96f
            )
            drawPath(
                path = wave2,
                brush = Brush.verticalGradient(0f to Color(0xFF1C4B7E), 1f to Color.Transparent),
                alpha = 0.58f
            )
            drawPath(
                path = wave3,
                brush = Brush.verticalGradient(0f to Color(0x802B6FB3), 1f to Color.Transparent),
                alpha = 0.42f
            )

            // --- Specular highlight (suptilno) ---
            val highlight = Path().apply {
                moveTo(0f, wave2Top)
                val amp = 6f
                val len = w / 1.2f
                var x = 0f
                val k = (Math.PI * 2).toFloat()
                while (x <= w) {
                    val y = wave2Top + amp * sin((x / len) * k + wave2Phase)
                    lineTo(x, y)
                    x += 6f
                }
            }
            drawPath(
                path = highlight,
                color = Color(0x66B8E3FF),
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )

            // === NEW: FOAM ON CRESTS ===
            // Pjena se crta kao niz kratkih crtica + sitnih kapljica točno uz vrhove srednjeg vala.
            run {
                val amp = 7f
                val len = w / 1.2f
                val step = 8f                         // razmak između uzoraka duž grebena
                val k = (Math.PI * 2).toFloat()
                var x = 0f
                while (x <= w) {
                    // Valna funkcija i njezina derivacija za nagib (tangenta)
                    val phase = (x / len) * k + wave2Phase
                    val y = wave2Top + amp * sin(phase)
                    val dy = amp * (k / len) * cos(phase)      // derivacija
                    // Brojila gdje je val "na vrhu": |dy| malo -> blizu vrha
                    val crestFactor = (1f - min(1f, abs(dy) * 14f)) // 0..1, 1 ~ plosnati vrh
                    if (crestFactor > 0.35f) {
                        // Orijentacija crte: tangenta vala
                        val angle = atan2(dy, 1f)
                        val nx = cos(angle)   // tangent x
                        val ny = sin(angle)
                        val lenFoam = 6f + 4f * crestFactor     // duljina crtice ovisno o “ravnini” vrha
                        val alpha = 0.35f + 0.35f * crestFactor // jača pjena na ravnijem vrhu
                        // Glavna pjenasta crtica (blago iznad površine)
                        drawLine(
                            color = Color(0xE6FFFFFF),
                            start = Offset(x - nx * lenFoam / 2f, y - ny * lenFoam / 2f - 1f),
                            end   = Offset(x + nx * lenFoam / 2f, y + ny * lenFoam / 2f - 1f),
                            strokeWidth = 1.6f,
                            alpha = alpha,
                            cap = StrokeCap.Round
                        )
                        // Kapljice (spray) – nekoliko točkica ispred smjera kretanja vala
                        val sprayCount = 2
                        repeat(sprayCount) { i ->
                            val offs = (i + 1) * 3.5f
                            val jitter = sin((x + i * 17f + t * 60f) * 0.12f) * 1.2f
                            drawCircle(
                                color = Color(0xCCFFFFFF),
                                radius = 0.9f + 0.4f * crestFactor,
                                center = Offset(x + nx * (offs + jitter), y + ny * (offs * 0.2f) - 1.5f),
                                alpha = alpha * 0.9f
                            )
                        }
                    }
                    x += step
                }
            }

            // --- DEPTH COLOR GRADING (strong dark toward bottom) ---
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0x11000000),
                    0.65f to Color(0x33091020),
                    1f to Color(0xBB070B18)
                ),
                size = androidx.compose.ui.geometry.Size(w, h)
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x33000000)),
                    center = Offset(cx, cy + h * 0.18f),
                    radius = radius * 1.05f
                ),
                size = androidx.compose.ui.geometry.Size(w, h)
            )

            // --- DEPTH BUBBLES (tri dubinske grupe + povremeni burst) ---
            fun drawBubbles(seed: Int, count: Int, radiusBase: Float, speed: Float, drift: Float, depth: Float, alpha: Float) {
                repeat(count) { k ->
                    val phase = (t * speed + (seed + k) * 0.137f) % 1f
                    val y = waterTop + (depth + h * 0.30f) - phase * (h * 0.30f)
                    val x = cx + (k - count / 2f) * 18f + sin(t * 0.5f + k) * drift
                    val r = radiusBase * (0.7f + 0.6f * ((k % 3) / 2f))
                    drawCircle(Color(0x55B4D2FF), r, Offset(x, y), alpha = alpha)
                    drawCircle(Color(0x88B4D2FF), r * 0.55f, Offset(x + 0.35f * r, y - 0.45f * r), alpha = alpha)
                }
            }
            drawBubbles(10, 4, 4.5f, 0.050f, 1.8f, 40f, 0.9f)
            drawBubbles(20, 6, 5.5f, 0.042f, 2.6f, 65f, 0.85f)
            drawBubbles(30, 3, 6.5f, 0.038f, 1.5f, 85f, 0.8f)

            val burstPhase = (t * 0.08f) % 1f
            if (burstPhase < 0.12f) {
                val bx = cx + sin(t * 1.7f) * 28f
                val by = waterTop + 90f
                repeat(3) { i ->
                    val off = i * 7f
                    drawCircle(Color(0x66B4D2FF), 3.2f, Offset(bx + off, by - i * 10f))
                }
            }

            // --- Bottom arc text (upright near rim) ---
            val arcText = "Requests  $usedRequests / $maxRequests"
            val textSizePx = 13.sp.toPx()
            val textPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = textSizePx
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.NORMAL
                )
            }
            val textRadius = radius * 0.955f
            val textStart = 160f          // desna donja
            val textSweep = -140f         // prema lijevo (da stoji uspravno)
            val oval = RectF(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
            val path = AndroidPath().apply { addArc(oval, textStart, textSweep) }
            val pathLen = PathMeasure(path, false).length
            val textLen = textPaint.measureText(arcText)
            val hOffset = ((pathLen - textLen) / 2f).coerceAtLeast(0f)
            val vOffset = -1.5f
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawTextOnPath(arcText, path, hOffset, vOffset, textPaint)
            }
        }

        // ===== FOREGROUND (title, price, labels, prices) =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gold (EUR/oz)", fontSize = 14.sp, color = Color(0xFFD0D7E6))
                Text(
                    text = euro(spot),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF2F4FA)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareSwatch(color = Color(0xFF1F6C35))
                Spacer(Modifier.width(10.dp))
                Text("BUY", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(18.dp))
                Text("SELL", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                SquareSwatch(color = Color(0xFF7A231D))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
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