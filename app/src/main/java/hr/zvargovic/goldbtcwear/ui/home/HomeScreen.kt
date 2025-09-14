package hr.zvargovic.goldbtcwear.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import hr.zvargovic.goldbtcwear.R
import hr.zvargovic.goldbtcwear.presentation.SpotViewModel
import hr.zvargovic.goldbtcwear.ui.components.StaleBadge

@Composable
fun HomeScreen(viewModel: SpotViewModel) {
    val eur by viewModel.eur.collectAsState()
    val ts  by viewModel.ts.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (eur) {
            null -> Text(stringResource(R.string.loading))   // "Učitavanje…"
            else -> {
                Text(
                    stringResource(R.string.gold_price_fmt, eur!!)  // "€%.2f /oz"
                )
                Spacer(Modifier.height(4.dp))
                StaleBadge(lastTimestampMs = ts)   // ⇦ pojavi se • stale kad je >6 min
            }
        }
    }
}