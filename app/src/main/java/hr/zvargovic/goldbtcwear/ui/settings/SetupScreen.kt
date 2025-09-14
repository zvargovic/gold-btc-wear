package hr.zvargovic.goldbtcwear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import hr.zvargovic.goldbtcwear.R

@Composable
fun SetupScreen(
    apiKey: String,
    alarmEnabled: Boolean,
    onBack: () -> Unit,
    onSave: (key: String, alarmEnabled: Boolean) -> Unit
) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    var alarm by remember(alarmEnabled) { mutableStateOf(alarmEnabled) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.title_setup),
            color = Color(0xFFFF7A00),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Label
            Text(
                text = stringResource(R.string.setup_api_key),
                color = Color(0xFFDCD3C8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Input (masked)
            BasicTextField(
                value = key,
                onValueChange = { key = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                visualTransformation = PasswordVisualTransformation(), // API ključ ne ostaje “goli”
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) { inner ->
                Box(Modifier.fillMaxSize()) {
                    if (key.isBlank()) {
                        Text(
                            text = stringResource(R.string.enter_api_key),
                            color = Color(0x77FFFFFF),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                    Box(Modifier.align(Alignment.CenterStart)) { inner() }
                }
            }

            // Alarm toggle
            ToggleChip(
                checked = alarm,
                onCheckedChange = { alarm = it },
                label = {
                    Text(
                        text = if (alarm)
                            stringResource(R.string.alarm_on)
                        else
                            stringResource(R.string.alarm_off),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                },
                toggleControl = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (alarm) Color(0xFF2FBF6B) else Color(0xFF888888),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = Color(0x332FBF6B),
                    uncheckedStartBackgroundColor = Color(0x22000000)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }

        // Footer buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0x22FFFFFF)),
                modifier = Modifier
                    .height(36.dp)
                    .weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.back),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSave(key.trim(), alarm) },
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFFF7A00)),
                modifier = Modifier
                    .height(36.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.save),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}