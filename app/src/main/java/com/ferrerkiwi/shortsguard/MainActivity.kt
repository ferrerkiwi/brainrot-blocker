package com.ferrerkiwi.shortsguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightBrainRotColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    outline = Color.Black,
)

private val DarkBrainRotColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    outline = Color.White,
)

private enum class ThemeMode {
    LIGHT,
    DARK,
}

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)
    private var protectionEnabled by mutableStateOf(true)
    private var themeMode by mutableStateOf(ThemeMode.DARK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = loadThemeMode()
        protectionEnabled = ProtectionPreferences.isEnabled(this)
        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val colors = if (themeMode == ThemeMode.LIGHT) {
                LightBrainRotColors
            } else {
                DarkBrainRotColors
            }

            MaterialTheme(colorScheme = colors) {
                ConfigureSystemBars(isLight = themeMode == ThemeMode.LIGHT)
                if (showSettings) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeSelected = ::saveThemeMode,
                        onBack = { showSettings = false },
                    )
                } else {
                    ShortsGuardScreen(
                        serviceEnabled = serviceEnabled,
                        protectionEnabled = protectionEnabled,
                        openAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onProtectionChanged = ::saveProtectionEnabled,
                        openSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isAccessibilityServiceEnabled(this)
    }

    private fun loadThemeMode(): ThemeMode {
        val storedValue = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(THEME_MODE_KEY, ThemeMode.DARK.name)
        return ThemeMode.entries.firstOrNull { it.name == storedValue } ?: ThemeMode.DARK
    }

    private fun saveThemeMode(themeMode: ThemeMode) {
        this.themeMode = themeMode
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(THEME_MODE_KEY, themeMode.name)
            .apply()
    }

    private fun saveProtectionEnabled(enabled: Boolean) {
        protectionEnabled = enabled
        ProtectionPreferences.setEnabled(this, enabled)
    }

    private companion object {
        const val PREFERENCES_NAME = "brainrot_preferences"
        const val THEME_MODE_KEY = "theme_mode"
    }
}

@Composable
private fun ConfigureSystemBars(isLight: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val barColor = if (isLight) Color.White else Color.Black
        window.statusBarColor = barColor.toArgb()
        window.navigationBarColor = barColor.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }
    }
}

@Composable
private fun ShortsGuardScreen(
    serviceEnabled: Boolean,
    protectionEnabled: Boolean,
    openAccessibilitySettings: () -> Unit,
    onProtectionChanged: (Boolean) -> Unit,
    openSettings: () -> Unit,
) {
    AppSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("!BrainRot", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = openSettings) {
                    Text("Settings")
                }
            }
            Text(
                when {
                    !protectionEnabled -> "Protection is paused"
                    serviceEnabled -> "Protection is on"
                    else -> "Protection needs permission"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (protectionEnabled) {
                    "When YouTube opens a Shorts player or Instagram opens a Reel, !BrainRot " +
                        "returns to the previous screen. On Instagram Home, it scrolls back " +
                        "when suggested posts begin."
                } else {
                    "!BrainRot is paused. YouTube Shorts will open normally until you turn " +
                        "protection back on."
                },
            )

            InfoCard {
                Text("Protection", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (protectionEnabled) "On" else "Off")
                    Switch(
                        checked = protectionEnabled,
                        onCheckedChange = onProtectionChanged,
                    )
                }
                Text("Pause blocking without changing Android Accessibility settings.")
            }

            InfoCard {
                Text("Private by design", style = MaterialTheme.typography.titleMedium)
                Text("• Watches only the official YouTube and Instagram apps")
                Text("• No network access, account access, screenshots, or event history")
                Text("• You can pause it here or turn it off in Android Accessibility settings")
            }

            PrimaryButton(
                onClick = openAccessibilitySettings,
                text = if (serviceEnabled) "Open Accessibility settings" else "Turn on !BrainRot",
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    AppSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Text("Choose how !BrainRot looks on your phone.")

            InfoCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeOption(
                        label = "Light",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeSelected(ThemeMode.LIGHT) },
                        modifier = Modifier,
                    )
                    ThemeOption(
                        label = "Dark",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeSelected(ThemeMode.DARK) },
                        modifier = Modifier,
                    )
                }
            }

            OutlinedButton(onClick = onBack) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun AppSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PrimaryButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        PrimaryButton(onClick = onClick, text = label, modifier = modifier)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
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
