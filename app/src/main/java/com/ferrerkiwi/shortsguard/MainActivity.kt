package com.ferrerkiwi.shortsguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShortsGuardScreen(
                    serviceEnabled = serviceEnabled,
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isAccessibilityServiceEnabled(this)
    }
}

@Composable
private fun ShortsGuardScreen(
    serviceEnabled: Boolean,
    openAccessibilitySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Shorts Guard", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (serviceEnabled) "Protection is on" else "Protection is off",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "When YouTube opens a Shorts player, Shorts Guard returns to the previous screen " +
                "and briefly says \"Shorts blocked\".",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Private by design", style = MaterialTheme.typography.titleMedium)
                Text("• Watches only the official YouTube app")
                Text("• No network access, account access, screenshots, or event history")
                Text("• You can turn it off any time in Android Accessibility settings")
            }
        }

        Button(onClick = openAccessibilitySettings) {
            Text(if (serviceEnabled) "Open Accessibility settings" else "Turn on Shorts Guard")
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val expectedClassName = ShortsGuardAccessibilityService::class.java.name
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == expectedClassName
        }
}
