package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import hr.zvargovic.goldbtcwear.data.AlertsStore

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Spot (za boje liste u AlertsScreen)
    val spot = 2315.40

    // --- PERSISTENCIJA ---
    val ctx = LocalContext.current
    val store = remember { AlertsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Živa lista u memoriji + inicijalno učitavanje iz DataStore-a
    val alerts = remember { mutableStateListOf<Double>() }
    LaunchedEffect(Unit) {
        alerts.clear()
        alerts.addAll(store.load())
    }
    // ----------------------

    // Ako kasnije dodaš popup/odabir u GoldStaticScreen, možeš koristiti ovo:
    val selectedAlert = rememberSaveable { mutableStateOf<Double?>(null) }

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            // ⬇️ Trenutna verzija GoldStaticScreen-a prima SAMO onOpenAlerts
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") }
            )
            // Kad nadogradiš GoldStaticScreen da prima alerts/selectedAlert:
            // GoldStaticScreen(
            //     onOpenAlerts = { navController.navigate("alerts") },
            //     alerts = alerts,
            //     selectedAlert = selectedAlert.value,
            //     onSelectAlert = { selectedAlert.value = it }
            // )
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

                    // ako je obrisan baš selektirani — makni selekciju (ako ga koristiš)
                    if (selectedAlert.value != null &&
                        kotlin.math.abs(selectedAlert.value!! - price) < 0.0001
                    ) {
                        selectedAlert.value = null
                    }

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

                        // opcionalno: novo dodani postavi kao selektirani (kad ga budeš koristio)
                        selectedAlert.value = value

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