package com.collabtable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.navigation.AppNavigation
import com.collabtable.app.ui.theme.CollabTableTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = PreferencesManager.getInstance(this)
            val themeMode by prefs.themeMode.collectAsState()
            val dynamicColor by prefs.dynamicColor.collectAsState()
            val amoledDark by prefs.amoledDark.collectAsState()

            val darkTheme =
                when (themeMode) {
                    PreferencesManager.THEME_MODE_LIGHT -> false
                    PreferencesManager.THEME_MODE_DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

            CollabTableTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, amoledDark = amoledDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
