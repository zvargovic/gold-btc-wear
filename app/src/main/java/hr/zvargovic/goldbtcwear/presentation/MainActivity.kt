package hr.zvargovic.goldbtcwear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import hr.zvargovic.goldbtcwear.ui.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Pokrećemo navigaciju – OVDJE JE KLJUČ!
            AppNavHost()
        }
    }
}