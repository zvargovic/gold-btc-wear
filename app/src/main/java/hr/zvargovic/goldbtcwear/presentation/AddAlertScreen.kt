package hr.zvargovic.goldbtcwear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text as WearText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AddAlertScreen(
    spot: Double?,
    onBack: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val locale = Locale.getDefault()
    val dfs = DecimalFormatSymbols.getInstance(locale)

    // Formatter za preview (gore iznad polja)
    val previewFmt = remember(locale) {
        (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }
    }

    var text by remember { mutableStateOf("") }

    /**
     * Robustan parser:
     * - dopušta razmake (i non-breaking space) i uklanja ih
     * - ako nema . ni , -> sve tretira kao integer (22000 -> 22000.0)
     * - ako ima . ili , -> ZADNJI je decimalni, svi prije su tisućice
     */
    fun parseLocaleDouble(input: String): Double? {
        var raw = input.replace("\\s|\\u00A0".toRegex(), "")
        if (raw.isEmpty()) return null

        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val lastSep = maxOf(lastDot, lastComma)

        return if (lastSep < 0) {
            // Bez separatora -> cijeli broj
            raw.toDoubleOrNull()
        } else {
            // ZADNJI separator = decimalni; ostale točke/zareze izbaci
            val sb = StringBuilder(raw.length)
            for (i in raw.indices) {
                val ch = raw[i]
                if ((ch == '.' || ch == ',') && i != lastSep) {
                    // tisućice -> preskoči
                    continue
                }
                sb.append(if ((ch == '.' || ch == ',') && i == lastSep) '.' else ch)
            }
            sb.toString().toDoubleOrNull()
        }
    }

    fun confirmIfValid() {
        val v = parseLocaleDouble(text)
        if (v != null && v > 0.0) onConfirm(v)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview s locale formatom (ako parsiranje uspije)
            val previewStr = parseLocaleDouble(text)?.let { previewFmt.format(it) }
                ?: run {
                    val zeroFmt = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                    zeroFmt.format(0.0)
                }

            WearText(
                text = "$previewStr eur",
                color = Color(0xFFDCD3C8),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )

            // Polje za unos — CRNA podloga; dimenzije ostaju kako su bile
            TextField(
                value = text,
                onValueChange = { s ->
                    // dopusti znamenke, točku, zarez i razmak (za tisućice)
                    val ok = s.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }
                    if (ok) text = s
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color(0xFFDCD3C8),
                    backgroundColor = Color(0xFF000000), // crna podloga polja
                    focusedIndicatorColor = Color(0xFFFF7A00),
                    unfocusedIndicatorColor = Color(0xFF444444),
                    cursorColor = Color(0xFFFF7A00)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )

            Spacer(Modifier.height(10.dp))

            // Gumbi — iste veličine kao Back na AlertsScreenu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    WearText("Back", color = Color.Black, fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { confirmIfValid() },
                    contentAlignment = Alignment.Center
                ) {
                    WearText("✓ Confirm", color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}