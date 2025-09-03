package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import hr.zvargovic.goldbtcwear.data.AlertsStore

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Spot koji koristiš za boje u listi
    val spot = 2315.40

    // --- PERSISTENCIJA ---
    val ctx = LocalContext.current
    val store = remember { AlertsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Lista u memoriji + inicijalno učitavanje iz DataStore-a
    val alerts = remember { mutableStateListOf<Double>() }
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(store.load())
    }
    // ----------------------

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") }
            )
        }

        composable("alerts") {
            AlertsScreen(
                onBack = {
                    navController.popBackStack()
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
            AddAlertScreen(
                spot = spot,
                onBack = {
                    navController.popBackStack()
                    Unit
                },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { kotlin.math.abs(it - value) < 0.0001 }
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