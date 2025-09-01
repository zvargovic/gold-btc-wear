package hr.zvargovic.goldbtcwear.ui
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlin.math.sin

/**
 * Osnova: isto ponašanje kao tvoj TintedPrice — narančasta baza preko koje nalijevamo tint.
 * Efekti su čisti vizualni (transform/alpha/boja) i ne utječu na layout širinu.
 */
@Composable
private fun BaseTintedPrice(
    modifier: Modifier = Modifier,
    text: String,
    baseColor: Color,
    tint: Color,
    tintAlpha: Float,
    fontSizeSp: Int,
    weight: FontWeight
) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // nalij tint samo na sadržaj (tekst)
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

/* ===== 1) WAVE Y — lagano valjanje gore-dolje ===== */
@Composable
fun TintedPrice_WaveY(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight,
    amplitudeDp: Float = 2.2f,     // koliko pikselâ gore-dolje (~2–4 dp)
    speed: Float = 0.9f,           // brzina vala
) {
    val inf = rememberInfiniteTransition(label = "waveY")
    val t by inf.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing)),
        label = "t"
    )
    val dy = (sin(t * speed) * amplitudeDp).dp
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer { translationY = dy.toPx() },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 2) WAVE X — vrlo suptilan drift lijevo-desno ===== */
@Composable
fun TintedPrice_WaveX(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight,
    amplitudeDp: Float = 1.6f,
    speed: Float = 0.8f
) {
    val inf = rememberInfiniteTransition(label = "waveX")
    val t by inf.animateFloat(
        0f, 2f * Math.PI.toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "t"
    )
    val dx = (sin(t * speed) * amplitudeDp).dp
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer { translationX = dx.toPx() },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 3) TILT — nježno njihanje (rotacija oko središta) ===== */
@Composable
fun TintedPrice_Tilt(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight,
    degrees: Float = 2.2f
) {
    val inf = rememberInfiniteTransition(label = "tilt")
    val a by inf.animateFloat(
        initialValue = -degrees, targetValue = degrees,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer { rotationZ = a },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 4) OPACITY PULSE — “disanje” svjetline ===== */
@Composable
fun TintedPrice_OpacityPulse(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "op")
    val a by inf.animateFloat(
        initialValue = 0.72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer { alpha = a },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 5) SCALE BOB — lagano “plutanje” skalom ===== */
@Composable
fun TintedPrice_ScaleBob(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "scale")
    val s by inf.animateFloat(
        0.98f, 1.02f,
        infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
        label = "s"
    )
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 6) COLOR PULSE — lagano miješanje boje prema tintu ===== */
@Composable
fun TintedPrice_ColorPulse(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "cp")
    val p by inf.animateFloat(
        0.45f, 0.85f,
        infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )
    // postižemo osjećaj pulsiranja boje jačanjem/sluabljenjem tinta
    BaseTintedPrice(
        modifier = Modifier,
        text = text, baseColor = baseColor, tint = tint, tintAlpha = p,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 7) GLOW PULSE — diskretan sjaj iza teksta ===== */
@Composable
fun TintedPrice_GlowPulse(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "glow")
    val a by inf.animateFloat(
        0.12f, 0.28f,
        infiniteRepeatable(tween(1400, easing = FastOutLinearInEasing), RepeatMode.Reverse),
        label = "ga"
    )
    val glowColor = tint.copy(alpha = a)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                // “halo” – dvaput nacrtamo sadržaj s većim scale/alpha pa original
                val save = drawContext.transform
                with(drawContext.canvas) {
                    // širi blur efekt imitiran skaliranjem
                }
                drawContent()
            }
    ) {
        // “halo” sloj (jednostavno: pravokutni tint SrcAtop preko teksta)
        BaseTintedPrice(
            modifier = Modifier.drawWithContent {
                drawContent()
                drawRect(glowColor, blendMode = BlendMode.SrcAtop)
            },
            text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
            fontSizeSp = fontSizeSp, weight = weight
        )
    }
}

/* ===== 8) SHIMMER SWEEP — animirani gradient preko teksta ===== */
@Composable
fun TintedPrice_ShimmerSweep(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "shim")
    val x by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "x"
    )
    // gradient koji “putuje” zdesna ulijevo
    val shimmer = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.35f),
            Color.Transparent
        ),
        start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
        end = androidx.compose.ui.geometry.Offset((x - 0.25f) * 600f, 0f)
    )
    BaseTintedPrice(
        modifier = Modifier.drawWithContent {
            drawContent()
            // Shimmer sloj preko teksta
            drawRect(shimmer, blendMode = BlendMode.SrcAtop)
        },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 9) FLOAT COMBO — lagani mix valjanja + skale + nagiba ===== */
@Composable
fun TintedPrice_FloatCombo(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "combo")
    val t by inf.animateFloat(
        0f, 2f * Math.PI.toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "t"
    )
    val dy = (sin(t) * 2.0f).dp
    val rot = sin(t * 0.7f) * 1.8f
    val s = 1f + sin(t * 0.9f) * 0.01f
    BaseTintedPrice(
        modifier = Modifier.graphicsLayer {
            translationY = dy.toPx()
            rotationZ = rot
            scaleX = s; scaleY = s
        },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}

/* ===== 10) UNDERLINE RIPPLE — mala valovita crta ispod ===== */
@Composable
fun TintedPrice_UnderlineRipple(
    text: String,
    baseColor: Color,
    tint: Color,
    fontSizeSp: Int,
    weight: FontWeight
) {
    val inf = rememberInfiniteTransition(label = "ul")
    val t by inf.animateFloat(
        0f, 2f * Math.PI.toFloat(),
        infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "t"
    )
    BaseTintedPrice(
        modifier = Modifier.drawWithContent {
            drawContent()
            // “val” ispod teksta (par piksela debljine)
            val w = size.width
            val h = size.height
            val y = h - 2.dp.toPx()
            val samples = 24
            val amp = 2.5f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                for (i in 0..samples) {
                    val x = w * i / samples
                    val yy = y + sin(t + i * 0.6f) * amp
                    lineTo(x, yy)
                }
            }
            drawPath(path, tint.copy(alpha = 0.55f), style = Stroke(width = 1.6f))
        },
        text = text, baseColor = baseColor, tint = tint, tintAlpha = 0.65f,
        fontSizeSp = fontSizeSp, weight = weight
    )
}