package com.ado.app.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ado.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SplashDurationMillis = 2500L

@Composable
fun SplashScreen(
    initialize: suspend () -> Unit,
    friendlyError: (Throwable) -> String,
    onFinished: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        error = null
        val startedAt = SystemClock.elapsedRealtime()
        val failure = try {
            initialize()
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            error
        }
        if (attempt == 0) {
            val remaining = SplashDurationMillis - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0) delay(remaining)
        }
        if (failure == null) onFinished() else error = friendlyError(failure)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.ado_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Unable to load ado: $message")
                Button(
                    onClick = { attempt += 1 },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
