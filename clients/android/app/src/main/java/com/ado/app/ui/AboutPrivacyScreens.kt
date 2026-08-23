package com.ado.app.ui

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ado.app.R

internal const val ADO_WEBSITE_URL = "https://www.cw-complex.com/ado/"
internal const val ADO_PRIVACY_URL = "https://www.cw-complex.com/ado/privacy-policy"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { installedVersionName(context) }

    AdoScaffold(title = "About Ado", onBack = onBack) { padding ->
        Column(
            modifier = padding
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        setImageResource(R.mipmap.ic_launcher)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        contentDescription = "Ado app icon"
                    }
                },
                modifier = Modifier.size(96.dp),
            )
            Text("Ado", style = MaterialTheme.typography.headlineMedium)
            Text("Version $versionName", color = MutedTextColor)
            Text(
                "A simple, local-first app for projects, tasks, checklists, and everyday lists.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { openExternalHttpUrl(context, ADO_WEBSITE_URL) }) {
                Text("Open Ado website")
            }
            Text(ADO_WEBSITE_URL, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    AdoScaffold(title = "Privacy Policy", onBack = onBack) { padding ->
        Column(
            modifier = padding
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Last updated: August 23, 2026", color = MutedTextColor)
            Text("Ado is designed as a local-first application.")
            Text(
                "Your projects, tasks, subtasks, and templates are stored on your device. " +
                    "Ado does not require an account and does not include advertising, analytics, or behavioral tracking.",
            )
            Text(
                "Calendar data is accessed only when you explicitly use calendar import. " +
                    "Imported information becomes part of Ado's local data.",
            )
            Text("Ado only sends information elsewhere when you explicitly export, print, or share it.")
            Text(
                "Deleting Ado or deleting data within the app may remove locally stored information. " +
                    "Copies you have previously exported or shared are outside Ado's control.",
            )
            Button(onClick = { openExternalHttpUrl(context, ADO_PRIVACY_URL) }) {
                Text("View full privacy policy online")
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun installedVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
