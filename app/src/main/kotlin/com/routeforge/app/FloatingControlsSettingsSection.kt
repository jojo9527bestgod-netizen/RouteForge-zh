package com.routeforge.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.routeforge.simulation.data.FloatingControlsPreferences

@Composable
fun FloatingControlsSettingsSection() {
    val context = LocalContext.current
    val preferences = remember(context) { FloatingControlsPreferences(context) }
    var permitted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var enabled by remember { mutableStateOf(preferences.enabled && permitted) }
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permitted = Settings.canDrawOverlays(context)
        if (enableAfterPermission) {
            preferences.enabled = permitted
            enableAfterPermission = false
        }
        enabled = preferences.enabled && permitted
    }

    fun openPermissionSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } catch (error: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (error: ActivityNotFoundException) {
                enableAfterPermission = false
                Toast.makeText(context, R.string.floating_controls_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.floating_controls_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked && !Settings.canDrawOverlays(context)) {
                            enableAfterPermission = true
                            openPermissionSettings()
                        } else {
                            preferences.enabled = checked
                            enabled = checked
                        }
                    },
                )
            }
            Text(stringResource(R.string.floating_controls_description), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (permitted) R.string.floating_controls_permission_granted else R.string.floating_controls_permission_missing),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { openPermissionSettings() }) {
                Text(stringResource(R.string.floating_controls_manage_permission))
            }
        }
    }
}
