package hr.zvargovic.goldbtcwear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text as WearText
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AddAlertScreen(
    spot: Double?,
    onBack: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val locale = Locale.getDefault()

    // Format za preview (gornji tekst "22.000,00 eur" / "22,000.00 eur" ovisno o lokali)
    val previewFmt = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    var text by remember { mutableStateOf("") }

    /**
     * Robusno parsiranje unosa:
     * - dopušta i '.' i ',' u istom stringu
     * - zadnji separator tretira kao decimalni, svi prethodni se brišu (tisućice)
     * - radi za većinu lokalnih unosnih navika
     */
    fun parseLocaleDouble(s: String): Double? {
        if (s.isBlank()) return null
        val raw = s.replace("\\s|\\u00A0".toRegex(), "") // makni razmake / non-breaking space

        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val lastSep = maxOf(lastDot, lastComma)

        val normalized = if (lastSep < 0) {
            // nema separatora → čisti integer/float bez razdjelnika
            raw
        } else {
            buildString(raw.length) {
                raw.forEachIndexed { i, ch ->
                    when {
                        (ch == '.' || ch == ',') && i == lastSep -> append('.') // decimalni
                        (ch == '.' || ch == ',') && i != lastSep -> { /* preskoči tisućice */ }
                        else -> append(ch)
                    }
                }
            }
        }

        return normalized.toDoubleOrNull()
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
            // Preview vrijednosti formatiran lokalno (ako se može parsirati), inače 0.00 eur
            val preview = parseLocaleDouble(text)?.let { previewFmt.format(it) + " eur" } ?: run {
                val zero = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }.format(0)
                "$zero eur"
            }
            WearText(
                text = preview,
                color = Color(0xFFDCD3C8),
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 12.dp)
            )

            // Polje za unos — CRNA pozadina; povećan textStyle da ne izgleda sitno i da ne “ulazi” u liniju
            TextField(
                value = text,
                onValueChange = { s ->
                    // dopusti znamenke + zarez/točku + razmak (nekad se upišu tisućice)
                    val ok = s.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }
                    if (ok) text = s
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 18.sp)),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color(0xFFDCD3C8),
                    backgroundColor = Color(0xFF000000),         // crna pozadina
                    focusedIndicatorColor = Color(0xFFFF7A00),
                    unfocusedIndicatorColor = Color(0xFF444444),
                    cursorColor = Color(0xFFFF7A00)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp) // mrvicu više da ne dođe do optičkog preklapanja s linijom
            )

            Spacer(Modifier.height(10.dp))

            // Gumbi – iste dimenzije kao "Back" koje već koristiš
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                // Back
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 80.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    WearText("Back", color = Color.Black, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(12.dp)) // razmak između gumba

                // Confirm
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 80.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { confirmIfValid() },
                    contentAlignment = Alignment.Center
                ) {
                    WearText("Confirm", color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}