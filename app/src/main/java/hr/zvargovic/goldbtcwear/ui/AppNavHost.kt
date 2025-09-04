package hr.zvargovic.goldbtcwear.ui
import hr.zvargovic.goldbtcwear.presentation.AddAlertScreen
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import hr.zvargovic.goldbtcwear.data.AlertsStore
import hr.zvargovic.goldbtcwear.data.SelectedAlertStore

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val spot = 2315.40

    // --- PERSISTENCIJA LISTE ---
    val ctx = LocalContext.current
    val store = remember { AlertsStore(ctx) }
    val selectedStore = remember { SelectedAlertStore(ctx) } // <<< NOVO
    val scope = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<Double>() }
    val selectedAlert = remember { mutableStateOf<Double?>(null) } // <<< NOVO

    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(store.load())
        selectedAlert.value = selectedStore.load() // <<< NOVO
    }

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") },
                alerts = alerts,
                selectedAlert = selectedAlert.value,                        // <<< NOVO
                onSelectAlert = { v ->                                     // <<< NOVO
                    selectedAlert.value = v
                    scope.launch { selectedStore.save(v) }
                }
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
                    scope.launch { store.save(alerts.toList()) }
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
                    navController.popBackStack()
                    Unit
                },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { kotlin.math.abs(it - value) < 0.0001 }
                    ) {
                        alerts.add(value)
                        alerts.sort()
                        scope.launch { store.save(alerts.toList()) }
                    }
                    navController.popBackStack()
                    Unit
                }
            )
        }
    }
}