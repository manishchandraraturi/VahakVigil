package com.manish.vahakvigil.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.manish.vahakvigil.R
import com.manish.vahakvigil.VahakVigilApp
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun ReadinessScreen(
    app: VahakVigilApp,
    onBack: () -> Unit,
    onContacts: () -> Unit,
    onMedical: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val contacts by app.contactRepository.getContacts().collectAsStateWithLifecycle(initialValue = emptyList())
    val medical by app.medicalProfileRepository.getProfile().collectAsStateWithLifecycle(initialValue = null)
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val fine = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationEnabled = remember(refresh) { locationEnabled(context) }
    val notificationAllowed = Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    val active = app.sensorRepository.isMonitoring.collectAsStateWithLifecycle().value
    val readiness = com.manish.vahakvigil.domain.model.RiderReadiness(
        sensorAvailable = app.sensorRepository.capabilities.isSupported,
        locationPermissionGranted = fine || coarse,
        gpsEnabled = locationEnabled,
        notificationsAllowed = notificationAllowed,
        emergencyContactsConfigured = contacts.isNotEmpty(),
        medicalProfileConfigured = medical?.let { it.name.isNotBlank() || it.bloodGroup.isNotBlank() || it.conditions.isNotBlank() || it.allergies.isNotBlank() || it.medications.isNotBlank() } == true,
        backgroundMonitoringActive = active
    )
    AppScreen {
        AppTopBar(title = stringResource(R.string.readiness_title), onBackClick = onBack)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.readiness_subtitle), color = MaterialTheme.vahakColors.mutedText)
        Spacer(Modifier.height(14.dp))
        AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ReadinessRow(stringResource(R.string.readiness_sensor), readiness.monitoringRequirementsAvailable, required = true)
                ReadinessRow(stringResource(R.string.readiness_location), readiness.locationAvailable, required = false) {
                    if (!fine && !coarse) permissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                ReadinessRow(stringResource(R.string.readiness_notifications), notificationAllowed, required = false) {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationAllowed) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else onSettings()
                }
                ReadinessRow(stringResource(R.string.readiness_contacts), readiness.emergencyContactsConfigured, required = false, onAction = onContacts)
                ReadinessRow(stringResource(R.string.readiness_medical), readiness.medicalProfileConfigured, required = false, onAction = onMedical)
                val monitoringStatus = when {
                    readiness.isMonitoringActive -> R.string.readiness_available
                    readiness.sensorAvailable -> R.string.readiness_monitoring_inactive
                    else -> R.string.readiness_missing
                }
                ReadinessRow(
                    stringResource(R.string.readiness_background),
                    readiness.isMonitoringActive,
                    required = !readiness.sensorAvailable,
                    statusOverride = monitoringStatus
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.readiness_required_note), color = MaterialTheme.vahakColors.mutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReadinessRow(label: String, available: Boolean, required: Boolean, statusOverride: Int? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(statusOverride ?: if (available) R.string.readiness_available else if (required) R.string.readiness_missing else R.string.readiness_optional),
            color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.vahakColors.mutedText,
            style = MaterialTheme.typography.bodySmall
        )
        if (onAction != null && !available) TextButton(onClick = onAction) { Text(stringResource(R.string.readiness_configure)) }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun locationEnabled(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled
    else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)
