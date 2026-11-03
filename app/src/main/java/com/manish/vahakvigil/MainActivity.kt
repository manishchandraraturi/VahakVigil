package com.manish.vahakvigil

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.manish.vahakvigil.navigation.VahakVigilNavHost
import com.manish.vahakvigil.ui.theme.ThemeManager
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val themeManager by lazy { ThemeManager(applicationContext) }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.manish.vahakvigil.data.preferences.LanguageManager(newBase).wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as? VahakVigilApp
        val isEmergency = app?.crashDetectionPipeline?.state?.value?.isEmergencyModeActive == true
        setEmergencyLockScreenFlags(isEmergency)

        if (app != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    app.crashDetectionPipeline.state.collect { state ->
                        setEmergencyLockScreenFlags(state.isEmergencyModeActive)
                    }
                }
            }
        }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val app = application as? VahakVigilApp
        val isEmergency = app?.crashDetectionPipeline?.state?.value?.isEmergencyModeActive == true
        setEmergencyLockScreenFlags(isEmergency)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as? VahakVigilApp
        val isEmergency = app?.crashDetectionPipeline?.state?.value?.isEmergencyModeActive == true
        setEmergencyLockScreenFlags(isEmergency)
    }

    internal fun setEmergencyLockScreenFlags(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enable)
            setTurnScreenOn(enable)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enable) {
                window.addFlags(flags)
            } else {
                window.clearFlags(flags)
            }
        }
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
