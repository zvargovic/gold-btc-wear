package hr.zvargovic.goldbtcwear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// EKRANI su u presentation paketu – OVI IMPORTI SU OBAVEZNI:

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Preživljava dok je proces živ (dovoljno da se lista vrati kad se vratiš s Back).
    val alerts = remember { mutableStateListOf<Double>() }
    val spot = 2315.40

    NavHost(navController = navController, startDestination = "gold") {

        composable("gold") {
            GoldStaticScreen(
                onOpenAlerts = { navController.navigate("alerts") }
            )
        }

        composable("alerts") {
            AlertsScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("addAlert") },
                onDelete = { price -> alerts.remove(price) },
                alerts = alerts,
                spot = spot
            )
        }

        composable("addAlert") {
            AddAlertScreen(
                spot = spot,
                onBack = { navController.popBackStack() },
                onConfirm = { value ->
                    if (value.isFinite() && value > 0.0 &&
                        alerts.none { kotlin.math.abs(it - value) < 0.0001 }
                    ) {
                        alerts.add(value)
                        alerts.sort()
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}