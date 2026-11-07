package com.manish.vahakvigil.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.R
import com.manish.vahakvigil.VahakVigilApp
import com.manish.vahakvigil.domain.model.NotificationReadinessPolicy
import com.manish.vahakvigil.domain.model.ReadinessCheckStatus
import com.manish.vahakvigil.domain.model.RiderReadiness
import com.manish.vahakvigil.domain.model.RiderReadinessSnapshot
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun ReadinessScreen(
    app: VahakVigilApp,
    onBack: () -> Unit,
    onContacts: () -> Unit,
    onMedical: () -> Unit
) {
    val context = LocalContext.current
    val contacts by app.contactRepository.getContacts().collectAsStateWithLifecycle(initialValue = emptyList())
    val medical by app.medicalProfileRepository.getProfile().collectAsStateWithLifecycle(initialValue = null)
    val monitoringActive by app.sensorRepository.isMonitoring.collectAsStateWithLifecycle()
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var locationRequestDenied by rememberSaveable { mutableStateOf(false) }
    var notificationRequestDenied by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-read OS-owned state every time this destination resumes (for example after Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = remember(context, refresh) {
        PermissionSnapshot(
            fineLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
            coarseLocation = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            notifications = NotificationReadinessPolicy.isAllowed(
                Build.VERSION.SDK_INT,
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            ),
            locationServicesEnabled = locationEnabled(context)
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationRequestDenied = !granted
        refresh++
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationRequestDenied = !granted
        refresh++
    }

    val validContactCount = RiderReadiness.countValidEmergencyContacts(contacts)
    val readiness = RiderReadiness.from(
        RiderReadinessSnapshot(
            hasAccelerometer = app.sensorRepository.capabilities.hasAccelerometer,
            hasGyroscope = app.sensorRepository.capabilities.hasGyroscope,
            locationPermissionGranted = permissions.fineLocation || permissions.coarseLocation,
            gpsEnabled = permissions.locationServicesEnabled,
            notificationsAllowed = permissions.notifications,
            validEmergencyContactCount = validContactCount,
            medicalProfileConfigured = medical?.hasMeaningfulEmergencyInformation == true,
            backgroundMonitoringActive = monitoringActive
        )
    )
    val statusCounts = listOf(
        readiness.accelerometerStatus,
        readiness.gyroscopeStatus,
        readiness.contactsStatus,
        readiness.locationStatus,
        readiness.notificationsStatus,
        readiness.medicalProfileStatus
    )
    val readyCount = statusCounts.count { it == ReadinessCheckStatus.READY }
    val attentionCount = statusCounts.count { it == ReadinessCheckStatus.NEEDS_ATTENTION }
    val unavailableCount = statusCounts.count { it == ReadinessCheckStatus.UNAVAILABLE }

    val requestLocation: () -> Unit = {
        if (locationRequestDenied) openApplicationSettings(context)
        else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val openLocationSettings: () -> Unit = {
        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .onFailure { openApplicationSettings(context) }
    }
    AppScreen {
        AppTopBar(title = stringResource(R.string.readiness_title), onBackClick = onBack)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.readiness_subtitle), color = MaterialTheme.vahakColors.mutedText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(readiness.overallStatus.titleResource()),
                    color = readiness.overallStatus.statusColor(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.readiness_check_counts, readyCount, attentionCount, unavailableCount),
                    color = MaterialTheme.vahakColors.headingText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(stringResource(R.string.readiness_not_guarantee), color = MaterialTheme.vahakColors.mutedText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ReadinessRow(
                    title = stringResource(R.string.readiness_accelerometer),
                    detail = stringResource(R.string.readiness_accelerometer_detail),
                    status = readiness.accelerometerStatus
                )
                ReadinessRow(
                    title = stringResource(R.string.readiness_gyroscope),
                    detail = stringResource(R.string.readiness_gyroscope_detail),
                    status = readiness.gyroscopeStatus
                )
                ReadinessRow(
                    title = stringResource(R.string.readiness_contacts),
                    detail = stringResource(R.string.readiness_contact_detail, validContactCount),
                    status = readiness.contactsStatus,
                    actionText = stringResource(if (readiness.emergencyContactsConfigured) R.string.readiness_manage else R.string.readiness_add_contact),
                    onAction = onContacts
                )
                ReadinessRow(
                    title = stringResource(R.string.readiness_medical),
                    detail = stringResource(R.string.readiness_medical_detail),
                    status = readiness.medicalProfileStatus,
                    actionText = stringResource(if (readiness.medicalProfileConfigured) R.string.readiness_manage else R.string.readiness_complete_profile),
                    onAction = onMedical
                )
                val locationAction = when {
                    !readiness.locationPermissionGranted -> requestLocation
                    !permissions.locationServicesEnabled -> openLocationSettings
                    else -> null
                }
                val locationActionLabel = when {
                    !readiness.locationPermissionGranted && locationRequestDenied -> R.string.readiness_open_app_settings
                    !readiness.locationPermissionGranted -> R.string.readiness_grant_location
                    !permissions.locationServicesEnabled -> R.string.readiness_enable_location
                    else -> null
                }
                ReadinessRow(
                    title = stringResource(R.string.readiness_location),
                    detail = stringResource(
                        if (!readiness.locationPermissionGranted) R.string.readiness_location_permission_detail
                        else if (!permissions.locationServicesEnabled) R.string.readiness_location_disabled_detail
                        else R.string.readiness_location_ready_detail
                    ),
                    status = readiness.locationStatus,
                    actionText = locationActionLabel?.let { stringResource(it) },
                    onAction = locationAction
                )
                val notificationAction = if (readiness.notificationsAllowed) null else ({
                    if (Build.VERSION.SDK_INT < NotificationReadinessPolicy.ANDROID_13_API_LEVEL || notificationRequestDenied) {
                        openNotificationSettings(context)
                    } else {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
                ReadinessRow(
                    title = stringResource(R.string.readiness_notifications),
                    detail = stringResource(
                        if (Build.VERSION.SDK_INT < NotificationReadinessPolicy.ANDROID_13_API_LEVEL) R.string.readiness_notifications_compat_detail
                        else if (readiness.notificationsAllowed) R.string.readiness_notifications_ready_detail
                        else R.string.readiness_notifications_denied_detail
                    ),
                    status = readiness.notificationsStatus,
                    actionText = if (readiness.notificationsAllowed) null else stringResource(if (notificationRequestDenied) R.string.readiness_open_notification_settings else R.string.readiness_allow_notifications),
                    onAction = notificationAction
                )
                ReadinessRow(
                    title = stringResource(R.string.readiness_background),
                    detail = stringResource(if (readiness.isMonitoringActive) R.string.readiness_monitoring_active_detail else R.string.readiness_monitoring_inactive_detail),
                    status = if (readiness.isMonitoringActive) ReadinessCheckStatus.READY else null
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.readiness_required_note), color = MaterialTheme.vahakColors.mutedText, style = MaterialTheme.typography.bodySmall)
    }
}

private data class PermissionSnapshot(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val notifications: Boolean,
    val locationServicesEnabled: Boolean
)

@Composable
private fun ReadinessRow(
    title: String,
    detail: String,
    status: ReadinessCheckStatus?,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.vahakColors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = colors.headingText, fontWeight = FontWeight.SemiBold)
            if (status != null) {
                Text(
                    stringResource(status.titleResource()),
                    color = status.statusColor(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(status.statusColor().copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Text(detail, color = colors.mutedText, style = MaterialTheme.typography.bodySmall)
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.height(44.dp)) { Text(actionText) }
        }
    }
}

@Composable
private fun ReadinessCheckStatus.titleResource(): Int = when (this) {
    ReadinessCheckStatus.READY -> R.string.readiness_ready
    ReadinessCheckStatus.NEEDS_ATTENTION -> R.string.readiness_needs_attention
    ReadinessCheckStatus.UNAVAILABLE -> R.string.readiness_unavailable
}

@Composable
private fun ReadinessCheckStatus.statusColor(): Color = when (this) {
    ReadinessCheckStatus.READY -> MaterialTheme.vahakColors.success
    ReadinessCheckStatus.NEEDS_ATTENTION -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFD180) else Color(0xFF8A4B00)
    ReadinessCheckStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun locationEnabled(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled
    else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)

private fun openApplicationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }.onFailure { openApplicationSettings(context) }
}
