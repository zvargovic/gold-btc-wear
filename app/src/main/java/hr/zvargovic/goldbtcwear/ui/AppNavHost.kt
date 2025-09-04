package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.zvargovic.goldbtcwear.data.AlertsStore
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Spot (za boje i usporedbe)
    val spot = 2315.40

    // --- DataStore setup ---
    val ctx = LocalContext.current
    val store = remember { AlertsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Jedinstvena lista alerta u memoriji
    val alerts = remember { mutableStateListOf<Double>() }

    // Učitaj iz DataStore-a
    LaunchedEffect(Unit) {
        runCatching { store.load() }
            .onSuccess { list ->
                alerts.clear()
                alerts.addAll(list)
            }
    }
    // -----------------------

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            // Proslijedi alerts tako da popup u Gold-u ima podatke
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") },
                alerts = alerts
            )
        }

        composable("alerts") {
            AlertsScreen(
                onBack = {
                    navController.popBackStack()   // vraća Boolean – zanemari
                    Unit
                },
                onAdd = { navController.navigate("addAlert") },
                onDelete = { price ->
                    alerts.remove(price)
                    // spremi nakon promjene
                    scope.launch { store.save(alerts.toList()) }
                },
                alerts = alerts,
                spot = spot
            )
        }

        composable("addAlert") {
            // KORISTI POTPUNO KVALIFICIRANO IME!
            hr.zvargovic.goldbtcwear.presentation.AddAlertScreen(
                spot = spot,
                onBack = {
                    navController.popBackStack()
                    Unit
                },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { abs(it - value) < 0.0001 }
                    ) {
                        alerts.add(value)
                        alerts.sort()
                        // spremi nakon dodavanja
                        scope.launch { store.save(alerts.toList()) }
                    }
                    navController.popBackStack()
                    Unit
                }
            )
        }
    }
}