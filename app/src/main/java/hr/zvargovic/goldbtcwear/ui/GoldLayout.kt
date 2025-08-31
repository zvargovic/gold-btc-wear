
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

enum class PriceService { TwelveData, Yahoo }



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

    // --- Orange theme boje ---
    val orangeDim   = Color(0x66FF7A00)   // prigušeni narančasti akcent
    val orangeLine  = Color(0xFFFF7A00)   // glavni narančasti akcent (spot + base cijene)
    val orangeLite  = Color(0xFFFFA040)
    val orangeDeep  = Color(0xFFCC7722)
    val warmWhite   = Color(0xFFDCD3C8)

    // JAKI tintovi (traženi): BUY/SELL + marker + “0.1%”
    val buyTint  = Color(0xFF2FBF6B) // jača, svjetlija zelena
    val sellTint = Color(0xFFE0524D) // jača crvena

    // Aktivni servis (placeholder; kasnije veži na stvarni state)
    var activeService by remember { mutableStateOf(PriceService.TwelveData) }

    // Animirani cilj mjehurića libele po servisu (+5 = TwelveData, -5 = Yahoo)
    val bubbleStep by animateFloatAsState(
        targetValue = if (activeService == PriceService.TwelveData) 5f else -5f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "bubbleStep"
    )

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

                    // maskiraj sve iznad vala
                    val cutTop = Path().apply {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, crestY(w))
                        var x = w; val step = 4f
                        while (x >= 0f) { lineTo(x, crestY(x)); x -= step }
                        lineTo(0f, crestY(0f)); close()
                    }
                    drawPath(cutTop, Color.White, blendMode = BlendMode.DstOut)

                    // Voda — tonirana na narančasto
                    val waterPath = Path().apply {
                        moveTo(0f, crestY(0f)); lineTo(w, crestY(w)); lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(
                        path = waterPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66FF7A00), // topli narančasti tint pri površini
                                Color(0xFF1A0A00)  // tamno smeđe/crno prema dnu
                            ),
                            startY = levelY, endY = h
                        ),
                        blendMode = BlendMode.Multiply
                    )
                    val depthBrush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color(0x33220000),
                        1f to Color(0xAA110000)
                    )
                    drawRect(
                        brush = depthBrush,
                        topLeft = Offset(0f, levelY),
                        size = Size(w, h - levelY),
                        blendMode = BlendMode.Multiply
                    )

                    // rub vala (feather + naglasci)
                    val crestPath = Path().apply {
                        moveTo(0f, crestY(0f))
                        var x = 0f; val step = 4f
                        while (x <= w) { lineTo(x, crestY(x)); x += step }
                    }
                    val feather = 28f
                    val featherBrush = Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color(0x33000000), orangeDim, Color.Transparent),
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

        // === 1b) Suptilni mjehurići koji izlaze iz vode i rasplinjuju se gore ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Re-derive trenutni level i valnu funkciju identičnu vodenoj,
            // kako bi mjehurići točno "izlazili" iz grebena vala.
            val baseLevel = waterLevel.toFloat()
            val levelY = h * baseLevel + (sin(t * 0.35f) * 0.0045f) * h

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

            // Proceduralni, “bez stanja” emiteri duž širine.
            // Svaki "bin" emituje jedan mjehurić po ciklusu; životni ciklus ~2.8s.
            val stepX = 18f
            val riseMin = 22f
            val riseMax = 46f
            val life = 2.8f
            val tNorm = (t / life)

            fun hash01(i: Int): Float {
                // jednostavan hash → [0,1)
                val x = ((i * 1664525) xor (i shl 13)) * 1013904223
                // & 0x7fffffff i / 1e9f da dobijemo nešto glatko, ali deterministicno
                return ((x and 0x7fffffff) % 1000000) / 1000000f
            }

            var x = 0f
            while (x <= w) {
                val i = (x / stepX).roundToInt()
                val seed = hash01(i)
                // svaka traka ima pomak u vremenu:
                val phase = (tNorm + seed * 0.73f)
                val frac = phase - floor(phase) // 0..1 unutar ciklusa

                // Početna pozicija — točno iznad grebena
                val y0 = crestY(x) - 2f

                // Easing za uspon (sporije na kraju)
                val e = (1f - (1f - frac) * (1f - frac))
                val rise = riseMin + (riseMax - riseMin) * (0.4f + 0.6f * seed)
                val y = y0 - e * rise

                // Diskretno: crtamo samo u gornjoj crnoj zoni (iznad vode + buffer),
                // i gasimo kad pređe ~gornjih 45% ekrana.
                if (y < levelY - 6f && y > h * 0.08f) {
                    // Maksimalna veličina mjehura ≈ visina fonta SPOT cijene (30.sp)
                    val spotPx = 30.sp.toPx()
                    fun flerp(a: Float, b: Float, t: Float) = a + (b - a) * t
                    val maxR = spotPx * 0.5f        // radijus ~ pola visine fonta
                    val minR = maxR * 0.25f         // najmanji mjehur (diskretno)
                    val baseR = flerp(minR, maxR, seed)
                    // Mjehurić lagano raste kroz životni ciklus, ali ne prelazi maxR
                    val r = (baseR * (0.85f + 0.35f * e)).coerceAtMost(maxR)

                    // Alpha opada kroz vrijeme; vrlo suptilno (da ne smeta UI-u)
                    val a = (0.18f * (1f - frac)) * 0.85f

                    // Blagi pomak udesno/lijevo (kao rasplinjavanje vjetrom)
                    val sway = (sin((t + seed * 7f) * 0.9f) * 2.0f)

                    // Glavni mjehurić
                    drawCircle(
                        color = Color.White.copy(alpha = a * 0.75f),
                        radius = r,
                        center = Offset(x + sway, y)
                    )
                    // Unutarnji highlight
                    drawCircle(
                        color = Color.White.copy(alpha = a),
                        radius = r * 0.55f,
                        center = Offset(x + sway + r * 0.35f, y - r * 0.35f)
                    )

                    // Na kraju života — “rasplinjavanje” u sitne čestice
                    if (frac > 0.65f) {
                        val k = ((frac - 0.65f) / 0.35f).coerceIn(0f, 1f) // 0..1
                        val dots = 3
                        for (d in 0 until dots) {
                            val ang = twoPi * (seed * 13f + d * 0.33f)
                            val rr = r * (1.6f + 0.6f * d)
                            val ox = cos(ang) * rr * k
                            val oy = sin(ang) * rr * k
                            drawCircle(
                                color = Color.White.copy(alpha = a * (0.5f * (1f - k))),
                                radius = r * (0.18f + 0.10f * d),
                                center = Offset(x + sway + ox, y - 2f + oy)
                            )
                        }
                    }
                }

                x += stepX
            }
        }

        // === 2) Minutni krug + kružeća točka (1 krug = 1h) — unutra + duplo veće točkice ===
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
                // tople “bubble” točkice
                drawCircle(Color(0x55FF7A00), dotR, Offset(x, y))
                drawCircle(Color(0x88FFC07A), dotR * 0.55f, Offset(x + dotR*dotHi*0.3f, y - dotR*dotHi*0.35f))
            }
            val millis = System.currentTimeMillis()
            val hourFrac = ((millis % 3_600_000L).toFloat() / 3_600_000f)
            val ang = Math.toRadians((hourFrac * 360f - 90f).toDouble()).toFloat()
            val px = cx + rBezel * cos(ang); val py = cy + rBezel * sin(ang)
            drawCircle(orangeLine, 4.6f, Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.55f), 2.5f, Offset(px + 0.8f, py - 0.8f))
        }

        // === 3) Lijeva SKALA — kompaktna; marker + “0.1%” u zeleno/crvenom ===
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
                val col = if (i == 0) orangeLite else orangeDim
                val sw = if (i == 0) 5.6f else 4.2f
                drawLine(col, p1, p2, strokeWidth = sw, cap = StrokeCap.Round)
            }

            // marker — sada striktno green/red (ne narančasti)
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

            // natpis na zamišljenoj 6. crtici (−0.6%), poravnat po sredini crtice
            val showPct = if (overload) deltaHr else markerSteps * tick.toDouble()
            val txt = pctStr(showPct)
            val baseLabelColor = if (showPct >= 0) buyTint else sellTint
            val softLabel = androidx.compose.ui.graphics.lerp(warmWhite, baseLabelColor, 0.55f)
            val txtPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                // Compose Color → ARGB int
                color = ((softLabel.alpha * 255).roundToInt() shl 24) or
                        ((softLabel.red * 255).roundToInt() shl 16) or
                        ((softLabel.green * 255).roundToInt() shl 8) or
                        ((softLabel.blue * 255).roundToInt())
                textSize = 11.sp.toPx()
                // malo niža vidljivost + poštuj blink alpha
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
                c.rotate(txtAng + 270f) // OSTAVLJENO točno prema referenci
                c.nativeCanvas.drawText(
                    txt,
                    -halfTextW,
                    baselineYOffset,
                    txtPaint
                )
                c.restore()
            }

            // --- LIBELA (kapsula po istom luku kao skala) ---
            // Geometrija libele: uži prsten između inner/outer
            val tubeInner = inner + 2f
            val tubeOuter = outer - 2f
            val tubeWidth = (tubeOuter - tubeInner).coerceAtLeast(5f) // ~6–8 px prema ekranu
            val tubeR = (tubeInner + tubeOuter) / 2f
            val tubeRect = Rect(
                left = cx - tubeR,
                top = cy - tubeR,
                right = cx + tubeR,
                bottom = cy + tubeR
            )

            // Stakleni “track” (poluprozirni narančasti, s naglaskom)
            drawArc(
                color = orangeDim.copy(alpha = 0.40f),
                startAngle = start,
                sweepAngle = span,
                useCenter = false,
                topLeft = tubeRect.topLeft,
                size = tubeRect.size,
                style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
            )
            // tanki highlight po gornjem rubu
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = start,
                sweepAngle = span,
                useCenter = false,
                topLeft = tubeRect.topLeft,
                size = tubeRect.size,
                style = Stroke(width = tubeWidth * 0.30f, cap = StrokeCap.Round)
            )
            // Cilj mjehurića po servisu izražen u "koracima" skale, animiran izvan Canvas-a
            val targetStep = bubbleStep
            val targetAng = start + (targetStep + maxTicks) * stepAng

            // Pozicija mjehurića na kapsuli
            val bRad = Math.toRadians(targetAng.toDouble()).toFloat()
            val bx = cx + tubeR * cos(bRad)
            val by = cy + tubeR * sin(bRad)

            // Veličina mjehurića ~ 70–80% debljine kapsule
            val bubbleR = tubeWidth * 0.75f

            // Glavni mjehurić (staklast, diskretan)
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = bubbleR,
                center = Offset(bx, by)
            )
            // unutarnji specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = bubbleR * 0.50f,
                center = Offset(bx + bubbleR * 0.35f, by - bubbleR * 0.35f)
            )
            // maleni “drop shadow” za volumen
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = bubbleR * 0.98f,
                center = Offset(bx, by + bubbleR * 0.20f),
                blendMode = BlendMode.Multiply
            )
        }

        // === 4) Foreground: SPOT + BUY/SELL brojke (vertikalno) ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gold (EUR/oz)", fontSize = 14.sp, color = orangeLine)
                Text(
                    euro(spotNow),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = orangeLine   // SPOT narančast
                )
            }
            Spacer(Modifier.height(36.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // BUY — narančasta baza + JAK zeleni štih
                TintedPrice(
                    text = euro(buy),
                    baseColor = orangeLine,
                    tint = buyTint,
                    tintAlpha = 0.65f,  // pojačano
                    fontSizeSp = 16,
                    weight = FontWeight.SemiBold
                )
                // SELL — narančasta baza + JAK crveni štih
                TintedPrice(
                    text = euro(sell),
                    baseColor = orangeLine,
                    tint = sellTint,
                    tintAlpha = 0.65f,  // pojačano
                    fontSizeSp = 16,
                    weight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))
        }

        // === 5) Requests po DNU — topliji luk: "Requests 123/500" ===
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

        // === 6) “MOKRI” EFEKT (narančasti overlay pod vodom) ===
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
                drawRect(orangeLine.copy(alpha = 0.20f), blendMode = BlendMode.Multiply)
                val stripeH = 6f
                var y = levelY + 8f
                val gloss = Color.White.copy(alpha = 0.05f)
                while (y < h) {
                    drawRect(gloss, topLeft = Offset(0f, y), size = Size(w, 1.2f))
                    y += stripeH + 2f
                }
            }
        }
    }
}

/**
 * Text s narančastom bazom + kolor-tint (BUY=green / SELL=red).
 * Offscreen + SrcAtop: tint se primjenjuje samo preko nacrtanog teksta (a ne pozadine).
 */
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