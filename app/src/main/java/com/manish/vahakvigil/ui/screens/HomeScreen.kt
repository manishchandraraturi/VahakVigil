package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.ProgressiveMotorcycleLineArt
import com.manish.vahakvigil.ui.components.PrimaryButton
import com.manish.vahakvigil.ui.components.QuickAccessCard
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.components.SoftDivider
import com.manish.vahakvigil.ui.components.StatusCard
import com.manish.vahakvigil.ui.components.ThemeToggle
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.vahakColors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.ui.screens.home.HomeViewModel

private const val HOME_MOTORCYCLE_TRACE_MS = 3_500

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onEmergencyContactsClick: () -> Unit = {},
    onMedicalClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onReadinessClick: () -> Unit = {},
    onRideStopped: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onEmergencyModeTriggered: () -> Unit = {},
    onOpenLiveMap: () -> Unit = {},
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val motorcycleDescription = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.motorcycle_illustration_description)
    val homeBikeProgress = remember { Animatable(0f) }
    var hasPlayedHomeBikeEntrance by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasPlayedHomeBikeEntrance) {
            homeBikeProgress.snapTo(1f)
        } else {
            homeBikeProgress.animateTo(1f, tween(durationMillis = HOME_MOTORCYCLE_TRACE_MS, easing = LinearEasing))
            hasPlayedHomeBikeEntrance = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(uiState.isEmergencyModeActive) {
        if (uiState.isEmergencyModeActive) {
            onEmergencyModeTriggered()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            viewModel.refreshDateTime()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var showDestinationPicker by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onPermissionsResult(permissions, context)
    }

    val statusTitle = if (uiState.isMonitoring) {
        if (!uiState.isSmsPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_monitoring_sms_disabled)
        } else if (!uiState.isLocationPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_monitoring_no_gps)
        } else {
            context.getString(com.manish.vahakvigil.R.string.home_monitoring_active)
        }
    } else if (uiState.capabilities.isSupported) {
        if (!uiState.isSmsPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_setup_sms)
        } else if (!uiState.isLocationPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_setup_location)
        } else {
            context.getString(com.manish.vahakvigil.R.string.home_all_ready)
        }
    } else {
        context.getString(com.manish.vahakvigil.R.string.home_sensors_unavailable)
    }

    val statusSubtitle = if (uiState.isMonitoring) {
        if (!uiState.isSmsPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_sms_disabled)
        } else if (!uiState.isLocationPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_no_gps)
        } else {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_active)
        }
    } else if (uiState.capabilities.isSupported) {
        if (!uiState.isSmsPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_dispatch_unavailable)
        } else if (!uiState.isLocationPermissionGranted) {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_setup_location)
        } else {
            context.getString(com.manish.vahakvigil.R.string.home_subtitle_standby)
        }
    } else {
        context.getString(com.manish.vahakvigil.R.string.home_subtitle_sensor_missing)
    }

    AppScreen(modifier = modifier) {
        GreetingHeader(
            currentDate = uiState.currentDateFormatted,
            greeting = uiState.greetingFormatted,
            timeRegionInfo = uiState.currentTimeFormatted,
            isDarkTheme = isDarkTheme,
            onThemeChange = onThemeChange,
            onProfileClick = onProfileClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val artHeight = if (maxWidth < 360.dp) 184.dp else 196.dp
            ProgressiveMotorcycleLineArt(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(artHeight)
                    .semantics { contentDescription = motorcycleDescription },
                progress = homeBikeProgress.value
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = artHeight - 14.dp)
                    .fillMaxWidth(0.68f).height(1.dp)
                    .background(MaterialTheme.vahakColors.divider.copy(alpha = 0.75f))
            )

            StatusCard(
                title = statusTitle,
                subtitle = statusSubtitle,
                isActive = uiState.isMonitoring,
                isReady = uiState.capabilities.isSupported && uiState.isSmsPermissionGranted && uiState.isLocationPermissionGranted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = artHeight - 8.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.TextButton(onClick = onReadinessClick) {
                Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.readiness_open))
            }
        }
        PrimaryButton(
            text = if (uiState.isMonitoring) androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_stop_ride) else androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_start_ride),
            subtitle = null,
            onClick = {
                if (!uiState.isMonitoring) showDestinationPicker = true
                else {
                    viewModel.toggleMonitoring(context)
                    onRideStopped()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            prominent = true
        )

        // Live Ride Map
        if (uiState.isMonitoring) {
            Spacer(modifier = Modifier.height(14.dp))
            com.manish.vahakvigil.ui.components.LiveRideMapCard(
                planningState = uiState.ridePlanning,
                ridePath = uiState.ridePath,
                isMonitoring = uiState.isMonitoring,
                onRequestPermissionClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onEditDestinationClick = { showDestinationPicker = true },
                onOpenFullMapClick = onOpenLiveMap
            )
        }

        // The home dashboard keeps the raw sensor values secondary to ride controls.
        if (uiState.isMonitoring) {
            Spacer(Modifier.height(18.dp))
            PrimarySensorDashboard(uiState = uiState, isDarkTheme = isDarkTheme)
            Spacer(Modifier.height(14.dp))
            RideIndicatorsDashboard(uiState = uiState)
        }

        Spacer(Modifier.height(20.dp))

        if (uiState.emergencyCountdownState == EmergencyCountdownState.COUNTING_DOWN) {
            val remainingSeconds = if (uiState.emergencyCountdownRemainingMillis > 0L) {
                (uiState.emergencyCountdownRemainingMillis + 999L) / 1000L
            } else {
                0L
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_emergency_check),
                        color = MaterialTheme.vahakColors.headingText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_possible_emergency),
                        color = if (isDarkTheme) Color(0xFFFF6B6B) else Color(0xFFC62828),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_help_if_no_response),
                        color = MaterialTheme.vahakColors.mutedText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$remainingSeconds",
                        color = MaterialTheme.vahakColors.headingText,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PrimaryButton(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_im_fine),
                        subtitle = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_cancel_check),
                        onClick = viewModel::cancelEmergencyCountdown,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else if (uiState.emergencyCountdownState == EmergencyCountdownState.CANCELLED) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_check_cancelled),
                color = MaterialTheme.vahakColors.mutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else if (uiState.emergencyCountdownState == EmergencyCountdownState.EXPIRED) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_check_expired),
                color = if (isDarkTheme) Color(0xFFFF6B6B) else Color(0xFFC62828),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        val actionStatusLabel = when (uiState.latestEmergencyActionResult?.status) {
            EmergencyActionResultStatus.NOT_TRIGGERED -> "Emergency action status: Not triggered"
            EmergencyActionResultStatus.TRIGGERED -> "Emergency action status: Triggered"
            EmergencyActionResultStatus.FAILED -> "Emergency action status: Failed"
            null -> null
        }
        if (actionStatusLabel != null && uiState.emergencyCountdownState != EmergencyCountdownState.IDLE) {
            Text(
                text = actionStatusLabel,
                color = if (uiState.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED) {
                    if (isDarkTheme) Color(0xFF80D8FF) else Color(0xFF0277BD)
                } else {
                    MaterialTheme.vahakColors.mutedText
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (uiState.permissionLimitations.isNotEmpty() && !uiState.isMonitoring) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_protection_limitations),
                        color = if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.permissionLimitations.forEach { limitation ->
                        Text(
                            text = "• $limitation",
                            color = MaterialTheme.vahakColors.mutedText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (showDestinationPicker) {
            DestinationPickerDialog(
                planningState = uiState.ridePlanning,
                isMonitoring = uiState.isMonitoring,
                onQueryChange = viewModel::updateRideDestinationQuery,
                onSearch = { viewModel.searchRideDestinations() },
                onSelect = viewModel::selectRideDestination,
                onClear = viewModel::clearRideDestination,
                onDismiss = { showDestinationPicker = false },
                onStart = { withoutDestination ->
                    if (withoutDestination) viewModel.clearRideDestination()
                    showDestinationPicker = false
                    if (!uiState.isMonitoring) {
                        val permissionsToRequest = buildList {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.POST_NOTIFICATIONS)
                            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                            add(android.Manifest.permission.SEND_SMS)
                        }.filter { perm ->
                            androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (permissionsToRequest.isNotEmpty()) permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        else viewModel.toggleMonitoring(context)
                    }
                }
            )
        }

        if (uiState.permissionExplanation != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::dismissPermissionExplanation,
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_permission_notice),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.vahakColors.headingText
                    )
                },
                text = {
                    Column {
                        Text(
                            text = uiState.permissionExplanation ?: "",
                            color = MaterialTheme.vahakColors.mutedText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        if (uiState.permissionLimitations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            uiState.permissionLimitations.forEach { limitation ->
                                Text(
                                    text = "• $limitation",
                                    color = if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.startMonitoringWithLimitations(context)
                        }
                    ) {
                        Text(
                            text = if (!uiState.isSmsPermissionGranted) "Proceed (Sensors only)" else "Continue",
                            color = MaterialTheme.vahakColors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = viewModel::dismissPermissionExplanation
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.action_cancel),
                            color = MaterialTheme.vahakColors.mutedText
                        )
                    }
                },
                containerColor = MaterialTheme.vahakColors.card
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_quick_access))

        Spacer(modifier = Modifier.height(12.dp))

        QuickAccessGrid(
            onMedicalClick = onMedicalClick,
            onContactsClick = onEmergencyContactsClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PrimarySensorDashboard(uiState: com.manish.vahakvigil.ui.screens.home.HomeUiState, isDarkTheme: Boolean) {
    val colors = MaterialTheme.vahakColors
    val context = androidx.compose.ui.platform.LocalContext.current
    val reading = uiState.latestReading
    val waiting = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_sensor_waiting)
    val acceleration = reading?.accelerationMagnitude?.takeIf(Float::isFinite)
        ?.let { String.format(java.util.Locale.getDefault(), "%.2f m/s²", it) } ?: waiting
    val gyroscope = reading?.gyroscopeMagnitude?.takeIf(Float::isFinite)
        ?.let { String.format(java.util.Locale.getDefault(), "%.2f rad/s", it) } ?: waiting

    SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_evidence_analysis))
    Spacer(Modifier.height(10.dp))
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2
    ) {
        SensorValueTile(
            label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_total_acceleration),
            value = acceleration,
            modifier = Modifier.weight(1f)
        )
        SensorValueTile(
            label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_gyro),
            value = gyroscope,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(10.dp))
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val motion = uiState.displayedMotionEvent ?: uiState.latestMotionEvent
            val (motionText, motionLevel) = when (motion?.eventType) {
                MotionEventType.COMBINED_IMPACT -> context.getString(com.manish.vahakvigil.R.string.home_combined_impact) to EvidenceStatusLevel.CRITICAL
                MotionEventType.HIGH_ACCELERATION -> context.getString(com.manish.vahakvigil.R.string.home_acceleration_spike) to EvidenceStatusLevel.WARNING
                MotionEventType.HIGH_GYROSCOPE -> context.getString(com.manish.vahakvigil.R.string.home_rotation_spike) to EvidenceStatusLevel.WARNING
                null -> context.getString(com.manish.vahakvigil.R.string.home_normal_baseline) to EvidenceStatusLevel.NORMAL
            }
            EvidenceRow(
                label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_motion_indicator),
                value = motionText,
                statusLevel = motionLevel,
                isDarkTheme = isDarkTheme
            )

            val (impactText, impactLevel) = when {
                uiState.latestQualifiedIncident != null -> context.getString(com.manish.vahakvigil.R.string.home_qualified_incident) to EvidenceStatusLevel.CRITICAL
                uiState.latestCandidateAccidentEvent != null -> context.getString(com.manish.vahakvigil.R.string.home_candidate_review) to EvidenceStatusLevel.WARNING
                else -> context.getString(com.manish.vahakvigil.R.string.home_no_candidate) to EvidenceStatusLevel.INFORMATIONAL
            }
            EvidenceRow(
                label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_impact_evidence_indicator),
                value = impactText,
                statusLevel = impactLevel,
                isDarkTheme = isDarkTheme
            )

            val (postImpactText, postImpactLevel) = when (uiState.latestPostImpactResult?.status) {
                PostImpactStatus.OBSERVING -> context.getString(com.manish.vahakvigil.R.string.home_stillness_observing) to EvidenceStatusLevel.ACTIVE
                PostImpactStatus.MOVING -> context.getString(com.manish.vahakvigil.R.string.home_motion_after_impact) to EvidenceStatusLevel.NORMAL
                PostImpactStatus.STILL -> context.getString(com.manish.vahakvigil.R.string.home_stillness_confirmed) to EvidenceStatusLevel.CRITICAL
                null -> context.getString(com.manish.vahakvigil.R.string.home_awaiting_qualification) to EvidenceStatusLevel.INFORMATIONAL
            }
            EvidenceRow(
                label = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_post_impact_indicator),
                value = postImpactText,
                statusLevel = postImpactLevel,
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
private fun SensorValueTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier.heightIn(min = 82.dp), cornerRadius = 18.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Text(value, color = MaterialTheme.vahakColors.headingText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun RideIndicatorsDashboard(uiState: com.manish.vahakvigil.ui.screens.home.HomeUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val location = uiState.rideLocationState as? com.manish.vahakvigil.domain.model.RideLocationState.Available
    val gpsState = when (uiState.rideLocationState) {
        com.manish.vahakvigil.domain.model.RideLocationState.PermissionDenied -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_permission_required)
        com.manish.vahakvigil.domain.model.RideLocationState.GpsDisabled -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_gps_disabled)
        com.manish.vahakvigil.domain.model.RideLocationState.LocationTemporarilyUnavailable -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_signal_lost)
        com.manish.vahakvigil.domain.model.RideLocationState.LocationUnavailable -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_location_unavailable)
        com.manish.vahakvigil.domain.model.RideLocationState.WaitingForLocation,
        com.manish.vahakvigil.domain.model.RideLocationState.Inactive -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_acquiring)
        is com.manish.vahakvigil.domain.model.RideLocationState.Available -> location?.accuracyMeters?.let {
            context.getString(com.manish.vahakvigil.R.string.home_gps_accuracy_value, it)
        } ?: androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_status_location_available)
    }
    val speed = location?.speedMps?.takeIf { it.isFinite() && it >= 0f }
        ?.let { context.getString(com.manish.vahakvigil.R.string.home_speed_value, it * 3.6f) }
        ?: androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_metric_unavailable)
    val startedAt = uiState.rideStartedAtEpochMillis
    val elapsed = if (startedAt != null) (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) else null
    val duration = elapsed?.let { formatRideDuration(it) }
        ?: androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_metric_unavailable)
    val distance = uiState.rideDistanceMeters?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { context.getString(com.manish.vahakvigil.R.string.home_distance_value, it / 1000.0) }
        ?: androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_metric_unavailable)
    val candidate = uiState.latestCandidateAccidentEvent
    val evidenceLevel = when (candidate?.confidenceScore?.evidenceLevel) {
        com.manish.vahakvigil.domain.model.EvidenceLevel.LOW -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_evidence_level_low)
        com.manish.vahakvigil.domain.model.EvidenceLevel.MEDIUM -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_evidence_level_medium)
        com.manish.vahakvigil.domain.model.EvidenceLevel.HIGH -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_evidence_level_high)
        null -> null
    }
    val engine = when {
        !uiState.isMonitoring -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_engine_waiting)
        candidate == null -> androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_engine_active)
        else -> context.getString(com.manish.vahakvigil.R.string.home_engine_evidence_value, evidenceLevel, candidate.confidenceScore.value)
    }

    SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_ride_indicators))
    Spacer(Modifier.height(10.dp))
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2
    ) {
        RideIndicatorTile(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_gps_indicator), gpsState, Modifier.weight(1f))
        RideIndicatorTile(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_speed_indicator), speed, Modifier.weight(1f))
        RideIndicatorTile(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_duration_indicator), duration, Modifier.weight(1f))
        RideIndicatorTile(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_distance_indicator), distance, Modifier.weight(1f))
        RideIndicatorTile(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_crash_engine_indicator), engine, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(6.dp))
    Text(
        androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_confidence_explanation),
        color = MaterialTheme.vahakColors.mutedText,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
}

@Composable
private fun RideIndicatorTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier.heightIn(min = 76.dp), cornerRadius = 18.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(label, color = MaterialTheme.vahakColors.mutedText, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(value, color = MaterialTheme.vahakColors.headingText, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

private fun formatRideDuration(durationMillis: Long): String {
    val minutes = durationMillis / 60_000L
    val hours = minutes / 60L
    return if (hours > 0L) "${hours}h ${minutes % 60L}m" else "${minutes}m"
}

@Composable
private fun DestinationPickerDialog(
    planningState: com.manish.vahakvigil.domain.model.RidePlanningState,
    isMonitoring: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (com.manish.vahakvigil.domain.model.RideDestination) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onStart: (withoutDestination: Boolean) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_plan_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = planningState.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_destination)) },
                    singleLine = true,
                    trailingIcon = {
                        androidx.compose.material3.TextButton(
                            onClick = onSearch
                        ) { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_search)) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                when {
                    planningState.isSearching -> Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_searching), color = MaterialTheme.vahakColors.mutedText)
                    planningState.searchFailed -> Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_search_unavailable), color = MaterialTheme.vahakColors.mutedText)
                }
                planningState.destination?.let { destination ->
                    Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_selected_destination, destination.label), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.TextButton(onClick = onClear) { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_clear_destination)) }
                }
                planningState.searchResults.take(5).forEach { destination ->
                    androidx.compose.material3.TextButton(onClick = { onSelect(destination) }, modifier = Modifier.fillMaxWidth()) {
                        Text(destination.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
                Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_start_without_destination_hint), color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onStart(false) }) {
                Text(androidx.compose.ui.res.stringResource(when {
                    isMonitoring -> com.manish.vahakvigil.R.string.ride_done
                    planningState.destination == null -> com.manish.vahakvigil.R.string.home_start_ride
                    else -> com.manish.vahakvigil.R.string.ride_start_with_destination
                }))
            }
        },
        dismissButton = {
            Row {
                if (!isMonitoring) androidx.compose.material3.TextButton(onClick = { onStart(true) }) { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_start_without_destination)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.action_cancel)) }
            }
        },
        containerColor = MaterialTheme.vahakColors.card
    )
}

@Composable
private fun GreetingHeader(
    currentDate: String,
    greeting: String,
    timeRegionInfo: String,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp)
        ) {
            Text(
                text = currentDate,
                color = colors.mutedText,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeRegionInfo,
                color = colors.mutedText.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = greeting,
                color = colors.headingText,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThemeToggle(
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_profile),
                modifier = Modifier
                    .clickable(onClick = onProfileClick)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                color = colors.primary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

enum class EvidenceStatusLevel {
    NORMAL,        // Green: safe / normal baseline / subsequent motion
    INFORMATIONAL, // Neutral / Off-white / Charcoal: informational / observing
    WARNING,       // Yellow / Amber: warning / abnormal / rotational spike / impact candidate
    CRITICAL,      // Red: critical / qualified incident / stillness confirmed / combined spike
    ACTIVE         // Blue / Accent: active system state / active evaluation window
}

private fun getEvidenceStatusColor(level: EvidenceStatusLevel, isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        when (level) {
            EvidenceStatusLevel.NORMAL -> Color(0xFF81C784)        // Light Green 300
            EvidenceStatusLevel.INFORMATIONAL -> Color(0xFFE2E2E8) // Soft Off-White
            EvidenceStatusLevel.WARNING -> Color(0xFFFFB74D)       // Amber / Orange 300
            EvidenceStatusLevel.CRITICAL -> Color(0xFFFF6B6B)      // Soft Vivid Red
            EvidenceStatusLevel.ACTIVE -> Color(0xFF80D8FF)        // Light Blue A100
        }
    } else {
        when (level) {
            EvidenceStatusLevel.NORMAL -> Color(0xFF2E7D32)        // Dark Green 800
            EvidenceStatusLevel.INFORMATIONAL -> Color(0xFF4A4A52) // Dark Slate
            EvidenceStatusLevel.WARNING -> Color(0xFFD97706)       // Dark Amber 700
            EvidenceStatusLevel.CRITICAL -> Color(0xFFC62828)      // Dark Red 800
            EvidenceStatusLevel.ACTIVE -> Color(0xFF0277BD)        // Deep Light Blue 800
        }
    }
}

@Composable
private fun EvidenceRow(
    label: String,
    value: String,
    statusLevel: EvidenceStatusLevel,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val valueColor = getEvidenceStatusColor(statusLevel, isDarkTheme)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            color = MaterialTheme.vahakColors.mutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = if (statusLevel == EvidenceStatusLevel.INFORMATIONAL) FontWeight.Normal else FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f).padding(start = 8.dp),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickAccessGrid(
    onMedicalClick: () -> Unit,
    onContactsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        cornerRadius = 28.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                QuickAccessCard(
                    title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_medical),
                    subtitle = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_editable_profile),
                    icon = AppIcon.Heart,
                    onClick = onMedicalClick,
                    modifier = Modifier.weight(1f)
                )
                SoftDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                )
                QuickAccessCard(
                    title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_contacts),
                    subtitle = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_trusted_contacts),
                    icon = AppIcon.Contact,
                    onClick = onContactsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            SoftDivider(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
            )

            Row(modifier = Modifier.weight(1f)) {
                QuickAccessCard(
                    title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_history),
                    subtitle = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_rides_count),
                    icon = AppIcon.Route,
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1f)
                )
                SoftDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                )
                QuickAccessCard(
                    title = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_settings),
                    subtitle = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_theme_safety),
                    icon = AppIcon.Gear,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LastRideHeader(
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_last_ride))

        Text(
            text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_history),
            modifier = Modifier
                .padding(end = 2.dp)
                .clickable(onClick = onHistoryClick),
            color = colors.primary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LastRideSummary(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors

    AppCard(
        modifier = modifier
            .fillMaxWidth(),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_no_rides),
                color = colors.headingText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.home_no_rides_body),
                color = colors.mutedText,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    val fakeRepo = object : com.manish.vahakvigil.domain.repository.SensorRepository {
        override val capabilities = com.manish.vahakvigil.domain.model.SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        override val isMonitoring = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val sensorReadings = kotlinx.coroutines.flow.emptyFlow<com.manish.vahakvigil.domain.model.SensorReading>()
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }

    VahakVigilTheme(dynamicColor = false) {
        VahakVigilGradientBackground {
            HomeScreen(
                viewModel = HomeViewModel(fakeRepo),
                isDarkTheme = false,
                onThemeChange = {}
            )
        }
    }
}
