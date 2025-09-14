package hr.zvargovic.goldbtcwear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import hr.zvargovic.goldbtcwear.R

@Composable
fun MarketClosedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.market_closed), // ⇦ prebačeno u strings.xml
            color = Color(0xFFFF5544),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}