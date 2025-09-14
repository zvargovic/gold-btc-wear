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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text as WearText
import hr.zvargovic.goldbtcwear.R
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

@Composable
fun AddAlertScreen(
    spot: Double?,
    onBack: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val locale = Locale.getDefault()

    val previewFmt = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    var text by remember { mutableStateOf("") }

    fun parseLocaleDouble(s: String): Double? {
        if (s.isBlank()) return null
        val raw = s.replace("\\s|\\u00A0".toRegex(), "")
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val lastSep = maxOf(lastDot, lastComma)

        val normalized = if (lastSep < 0) {
            raw
        } else {
            buildString(raw.length) {
                raw.forEachIndexed { i, ch ->
                    when {
                        (ch == '.' || ch == ',') && i == lastSep -> append('.')
                        (ch == '.' || ch == ',') && i != lastSep -> {}
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
            val preview = parseLocaleDouble(text)?.let { previewFmt.format(it) + " " + stringResource(R.string.ccy_eur_lower) }
                ?: run {
                    val zero = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }.format(0)
                    "$zero ${stringResource(R.string.ccy_eur_lower)}"
                }

            WearText(
                text = preview,
                color = Color(0xFFDCD3C8),
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 12.dp)
            )

            TextField(
                value = text,
                onValueChange = { s ->
                    val ok = s.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }
                    if (ok) text = s
                },
                singleLine = true,
                placeholder = { WearText(stringResource(R.string.add_alert_placeholder), fontSize = 14.sp, color = Color(0xFF9A9A9A)) },
                textStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 18.sp)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { confirmIfValid() }
                ),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color(0xFFDCD3C8),
                    backgroundColor = Color(0xFF000000),
                    focusedIndicatorColor = Color(0xFFFF7A00),
                    unfocusedIndicatorColor = Color(0xFF444444),
                    cursorColor = Color(0xFFFF7A00)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
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
                    WearText(stringResource(R.string.back), color = Color.Black, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Confirm
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 80.dp)
                        .background(Color(0xFFFF7A00), RoundedCornerShape(12.dp))
                        .clickable { confirmIfValid() },
                    contentAlignment = Alignment.Center
                ) {
                    WearText(stringResource(R.string.confirm), color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}