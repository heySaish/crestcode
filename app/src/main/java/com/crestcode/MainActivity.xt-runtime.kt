package com.crestcode

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.crestcode.runtime.AlpineManager
import com.crestcode.core.settings.SettingsManager

class MainActivity : ComponentActivity() {

    private lateinit var alpineManager: AlpineManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val t0 = System.currentTimeMillis()
        android.util.Log.d("XT_STARTUP", "🚀 MainActivity.onCreate START")

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        alpineManager = AlpineManager(this)
        settingsManager = SettingsManager(applicationContext)

        android.util.Log.d(
            "XT_STARTUP",
            "⏱️ AlpineManager & SettingsManager created (${System.currentTimeMillis() - t0}ms)"
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Crest UI / Monaco WebView will be added here.
                }
            }
        }

        android.util.Log.d(
            "XT_STARTUP",
            "🏁 MainActivity ready (${System.currentTimeMillis() - t0}ms)"
        )
    }
}
