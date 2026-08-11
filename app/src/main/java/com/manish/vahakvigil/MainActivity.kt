package com.manish.vahakvigil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.manish.vahakvigil.navigation.VahakVigilNavHost
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.ThemeManager
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val themeManager by lazy { ThemeManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by themeManager.isDarkTheme.collectAsState(initial = false)
            val scope = rememberCoroutineScope()

            VahakVigilTheme(darkTheme = isDarkTheme) {
                VahakVigilGradientBackground {
                    VahakVigilNavHost(
                        isDarkTheme = isDarkTheme,
                        onThemeChange = { enabled ->
                            scope.launch {
                                themeManager.setDarkTheme(enabled)
                            }
                        }
                    )
                }
            }
        }
    }
}
