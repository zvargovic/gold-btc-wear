package hr.zvargovic.goldbtcwear.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.Text
import hr.zvargovic.goldbtcwear.R
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun StaleBadge(lastTimestampMs: Long?, thresholdMinutes: Int = 6) {
    if (lastTimestampMs == null) return

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000) // osvježava procjenu starosti svakih 30s
            now = System.currentTimeMillis()
        }
    }

    val stale = max(0, now - lastTimestampMs) > thresholdMinutes * 60_000L
    if (stale) {
        Text(stringResource(R.string.stale_badge))
    }
}