package com.milestone.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.milestone.app.ui.navigation.MilestoneNavHost
import com.milestone.app.ui.theme.MilestoneTheme

class MainActivity : ComponentActivity() {
    private var openTimer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status/navigation bars with dark icons, matching the light Figma design.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Only on a fresh launch; rotation/process restore must not re-open the timer.
        if (savedInstanceState == null) openTimer = intent?.getBooleanExtra(EXTRA_OPEN_TIMER, false) == true
        setContent {
            MilestoneTheme {
                // No root inset padding: Scaffold pads the content, and the white bottom bar draws behind
                // the gesture handle itself (navigationBarsPadding inside BottomBar).
                Surface(Modifier.fillMaxSize()) {
                    MilestoneNavHost(openTimer = openTimer, onTimerOpened = { openTimer = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMER, false)) openTimer = true
    }

    companion object {
        const val EXTRA_OPEN_TIMER = "open_timer"
    }
}
