package com.luna.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luna.music.playback.PlayerBridge
import com.luna.music.ui.AppRoot
import com.luna.music.ui.theme.LunaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlayerBridge.connect(applicationContext)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            LunaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }
}
