package com.ado.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ado.app.data.AdoRepository
import com.ado.app.data.ApiClient
import com.ado.app.data.RoomLocalStore
import com.ado.app.data.SettingsStore
import com.ado.app.ui.AppNav

private val AdoDarkBlueColors = darkColorScheme(
    primary = Color(0xFF9ECBE6),
    onPrimary = Color(0xFF143246),
    primaryContainer = Color(0xFF365C72),
    onPrimaryContainer = Color(0xFFDCECF5),
    secondary = Color(0xFFADC9D9),
    onSecondary = Color(0xFF183440),
    secondaryContainer = Color(0xFF35505F),
    onSecondaryContainer = Color(0xFFE1EBF0),
    tertiary = Color(0xFFC6D8A5),
    onTertiary = Color(0xFF29331F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF683B42),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF263E50),
    onBackground = Color(0xFFE8EFF3),
    surface = Color(0xFF2D4759),
    onSurface = Color(0xFFE8EFF3),
    surfaceVariant = Color(0xFF365365),
    onSurfaceVariant = Color(0xFFC8D6DE),
    outline = Color(0xFF8299A7),
    outlineVariant = Color(0xFF4D6778),
)

@Composable
fun AdoApp() {
    val context = LocalContext.current.applicationContext
    val settingsStore = remember { SettingsStore(context) }
    val repository = remember {
        AdoRepository(
            apiClient = ApiClient(settingsStore),
            localStore = RoomLocalStore(context),
            settingsStore = settingsStore,
        )
    }

    MaterialTheme(colorScheme = AdoDarkBlueColors) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppNav(repository = repository, settingsStore = settingsStore)
        }
    }
}
