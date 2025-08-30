package hr.zvargovic.goldbtcwear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import hr.zvargovic.goldbtcwear.ui.GoldStaticScreen   // ⬅⬅ OVAJ IMPORT MORA POSTOJATI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldStaticScreen(Modifier.fillMaxSize()) }
    }
}