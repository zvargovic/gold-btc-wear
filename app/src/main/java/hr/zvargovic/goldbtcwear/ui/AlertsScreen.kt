package hr.zvargovic.goldbtcwear.ui
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Text as WearText
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Double) -> Unit = {},
    alerts: List<Double> = emptyList(),
    spot: Double? = null
) {
    val orange = Color(0xFFFF7A00)
    val green  = Color(0xFF2FBF6B)
    val red    = Color(0xFFE0524D)
    val warm   = Color(0xFFDCD3C8)

    val locale = Locale.getDefault()
    val fmt = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    fun money(amount: Double): String = fmt.format(amount) + " eur"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 10.dp)
    ) {
        // Naslov
        WearText(
            text = "Alerts",
            color = orange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )

        // Lista ili placeholder
        if (alerts.isEmpty()) {
            WearText(
                text = "No alerts – add one with +",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp, bottom = 48.dp) // ostavi prostora za Back
            ) {
                items(alerts) { price ->
                    val txtColor = when {
                        spot == null -> warm
                        price < spot -> red
                        else         -> green
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cijena
                        WearText(
                            text = money(price),
                            color = txtColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.width(10.dp))  // ovdje točno podešavaš razmak

                        // Kanta
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete alert",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier
                                .size(22.dp)              // veličina ikone
                                .clickable { onDelete(price) }
                                .padding(start = 6.dp)    // razmak lijevo od cijene
                        )
                    }
                }
            }
        }

        // + gumb (plutajući) — dimenzije i pozicija ostavljene iste
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 46.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(orange)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            WearText(
                text = "+",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Back “pill” — dimenzije ostavljene kako si tražio
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .widthIn(min = 64.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(orange)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            WearText(
                text = "Back",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}