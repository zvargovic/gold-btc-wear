package hr.zvargovic.goldbtcwear.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * Full-screen ekran s jednim velikim STOP gumbom.
 */
class AlarmStopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StopUi(onStop = { AlarmService.stop(this); finish() }) }
    }
}

@Composable
private fun StopUi(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(48.dp)
                .background(Color(0xFFFF7A00), RoundedCornerShape(16.dp))
                .clickable { onStop() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "STOP", color = Color.Black, fontSize = 18.sp)
        }
    }
}