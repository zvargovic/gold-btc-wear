package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import hr.zvargovic.goldbtcwear.data.AlertsStore
import hr.zvargovic.goldbtcwear.data.SelectedAlertStore
import hr.zvargovic.goldbtcwear.data.SettingsStore
import hr.zvargovic.goldbtcwear.ui.AlertsScreen
import hr.zvargovic.goldbtcwear.ui.settings.SetupScreen
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val spot = 2315.40

    val ctx = LocalContext.current
    val alertsStore = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) }
    val settingsStore = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) }

    // Settings (API key + alarm) preko flowova
    val apiKey by settingsStore.apiKeyFlow.collectAsState(initial = "")
    val alarmEnabled by settingsStore.alarmEnabledFlow.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(alertsStore.load())
        selectedAlert.value = selectedStore.load()
    }

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") },
                alerts = alerts,
                selectedAlert = selectedAlert.value,
                onSelectAlert = { v ->
                    selectedAlert.value = v
                    scope.launch { selectedStore.save(v) }
                },
                    onOpenSetup = { navController.navigate("setup") }   // <<< DODANO
            )
        }

        composable("alerts") {
            AlertsScreen(
                onBack = {
                    navController.popBackStack(); Unit
                },
                onAdd = { navController.navigate("addAlert") },
                onDelete = { price ->
                    alerts.remove(price)
                    scope.launch { alertsStore.save(alerts.toList()) }
                    if (selectedAlert.value != null &&
                        kotlin.math.abs(selectedAlert.value!! - price) < 0.0001
                    ) {
                        selectedAlert.value = null
                        scope.launch { selectedStore.save(null) }
                    }
                },
                alerts = alerts,
                spot = spot
            )
        }

        composable("addAlert") {
            AddAlertScreen(
                spot = spot,
                onBack = {
                    navController.popBackStack(); Unit
                },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { kotlin.math.abs(it - value) < 0.0001 }
                    ) {
                        alerts.add(value)
                        alerts.sort()
                        scope.launch { alertsStore.save(alerts.toList()) }
                    }
                    navController.popBackStack(); Unit
                }
            )
        }

        composable("setup") {
            SetupScreen(
                apiKey = apiKey,
                alarmEnabled = alarmEnabled,
                onBack = { navController.popBackStack() },
                onSave = { newKey, alarm ->
                    scope.launch { settingsStore.saveAll(newKey, alarm) }
                    navController.popBackStack()
                }
            )
        }
    }
}