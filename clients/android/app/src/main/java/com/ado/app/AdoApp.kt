package com.ado.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ado.app.data.AdoRepository
import com.ado.app.data.ApiClient
import com.ado.app.data.RoomLocalStore
import com.ado.app.data.SettingsStore
import com.ado.app.ui.AppNav

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

    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppNav(repository = repository, settingsStore = settingsStore)
        }
    }
}
