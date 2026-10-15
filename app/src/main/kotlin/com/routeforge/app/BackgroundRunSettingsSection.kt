package com.routeforge.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun BackgroundRunSettingsSection() {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var exempt by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openSetting(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(context, R.string.background_settings_unavailable, Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Toast.makeText(context, R.string.background_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.background_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.background_settings_description), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (exempt) R.string.background_battery_exempt else R.string.background_battery_optimized),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                enabled = !exempt,
                onClick = {
                    openSetting(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                },
            ) { Text(stringResource(R.string.background_request_exemption)) }
            TextButton(onClick = {
                openSetting(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.background_open_app_settings)) }
        }
    }
}
