package hr.zvargovic.goldbtcwear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

@Composable
fun SetupScreen(
    apiKey: String,
    alarmEnabled: Boolean,
    onBack: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var alarm by remember(alarmEnabled) { mutableStateOf(alarmEnabled) }

    val focusRequester = remember { FocusRequester() }

    val orange      = Color(0xFFFF7A00)
    val orangeLite  = Color(0xFFFFA040)
    val warmWhite   = Color(0xFFDCD3C8)
    val fieldBg     = Color(0xFF151515)
    val fieldStroke = Color(0x33FFFFFF)
    val chipBg      = Color(0xFF1E1E1E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Setup",
                color = orange,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "TwelveData API ključ",
                color = warmWhite,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(fieldBg)
                    .border(1.dp, fieldStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = warmWhite,
                        fontSize = 14.sp
                    ),
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation()
                )

                if (key.isEmpty()) {
                    Text(
                        text = "API key",
                        color = Color(0x77FFFFFF),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Chip(
                onClick = { focusRequester.requestFocus() },
                label = { Text("Unesi na mobitelu") },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.chipColors(
                    backgroundColor = chipBg,
                    contentColor = orange
                )
            )

            Spacer(Modifier.height(6.dp))

            ToggleChip(
                checked = showKey,
                onCheckedChange = { checked -> showKey = checked },
                label = { Text("Pokaži API key") },
                toggleControl = { Switch(checked = showKey) },
                modifier = Modifier.fillMaxWidth(),
                colors = ToggleChipDefaults.toggleChipColors(
                    uncheckedStartBackgroundColor = chipBg,
                    uncheckedEndBackgroundColor = chipBg,
                    uncheckedContentColor = warmWhite,
                    uncheckedToggleControlColor = warmWhite,
                    checkedStartBackgroundColor = orange,
                    checkedEndBackgroundColor = orangeLite,
                    checkedContentColor = Color.Black,
                    checkedToggleControlColor = Color.Black
                )
            )

            Spacer(Modifier.height(10.dp))

            ToggleChip(
                checked = alarm,
                onCheckedChange = { checked -> alarm = checked },
                label = { Text("Alarm kad alert pogodi spot") },
                toggleControl = { Switch(checked = alarm) },
                modifier = Modifier.fillMaxWidth(),
                colors = ToggleChipDefaults.toggleChipColors(
                    uncheckedStartBackgroundColor = chipBg,
                    uncheckedEndBackgroundColor = chipBg,
                    uncheckedContentColor = warmWhite,
                    uncheckedToggleControlColor = warmWhite,
                    checkedStartBackgroundColor = orange,
                    checkedEndBackgroundColor = orangeLite,
                    checkedContentColor = Color.Black,
                    checkedToggleControlColor = Color.Black
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    text = "Back",
                    onClick = onBack,
                    style = ActionButtonStyle.Filled(
                        background = orange,
                        content = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Save",
                    onClick = { onSave(key.trim(), alarm) },
                    style = ActionButtonStyle.Filled(
                        background = orange,
                        content = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Savjet: fokusiraj polje, zatim povuci prema gore i izaberi “Use phone keyboard”.",
                color = Color(0xFF9A9A9A),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private sealed class ActionButtonStyle {
    data class Filled(val background: Color, val content: Color) : ActionButtonStyle()
    data class Outlined(val borderColor: Color, val textColor: Color) : ActionButtonStyle()
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    style: ActionButtonStyle,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    corner: Dp = 16.dp,
    horizontalPad: Dp = 0.dp
) {
    val shape = RoundedCornerShape(corner)
    when (style) {
        is ActionButtonStyle.Filled -> {
            Button(
                onClick = onClick,
                modifier = modifier
                    .height(height)
                    .padding(horizontal = horizontalPad),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = style.background,
                    contentColor = style.content
                )
            ) { Text(text) }
        }
        is ActionButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier
                    .height(height)
                    .padding(horizontal = horizontalPad),
                shape = shape,
                border = ButtonDefaults.outlinedButtonBorder(
                    borderWidth = 2.dp,
                    borderColor = style.borderColor
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = style.textColor
                )
            ) { Text(text) }
        }
    }
}