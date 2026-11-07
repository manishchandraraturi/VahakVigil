package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.export.SensorCsvExporter
import com.manish.vahakvigil.ui.screens.ridehistory.SensorCsvExportStatus
import com.manish.vahakvigil.ui.components.AppCard
import com.manish.vahakvigil.ui.components.AppIcon
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.CircleIcon
import com.manish.vahakvigil.ui.components.ActualRecordedRideMap
import com.manish.vahakvigil.ui.components.GoogleMapsConfiguration
import com.manish.vahakvigil.ui.components.SectionTitle
import com.manish.vahakvigil.ui.screens.ridehistory.RideHistoryViewModel
import com.manish.vahakvigil.ui.theme.VahakVigilTheme
import com.manish.vahakvigil.ui.theme.VahakVigilGradientBackground
import com.manish.vahakvigil.ui.theme.vahakColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel,
    onBackClick: () -> Unit,
    onRideClick: (String) -> Unit,
    onIncidentsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<RideSession?>(null) }
    AppScreen(modifier = modifier) {
        AppTopBar(title = androidx.compose.ui.res.stringResource(R.string.home_history), onBackClick = onBackClick)
        Spacer(Modifier.height(26.dp))
        SectionTitle(text = androidx.compose.ui.res.stringResource(R.string.history_title))
        TextButton(onClick = onIncidentsClick) {
            Text(androidx.compose.ui.res.stringResource(R.string.incident_history_open))
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.isLoading -> Text(androidx.compose.ui.res.stringResource(R.string.ride_history_loading), color = MaterialTheme.vahakColors.mutedText)
            state.error && state.sessions.isEmpty() -> Text(androidx.compose.ui.res.stringResource(R.string.ride_history_error), color = MaterialTheme.vahakColors.mutedText)
            state.sessions.isEmpty() -> AppCard(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleIcon(icon = AppIcon.Route, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.history_empty_title), color = MaterialTheme.vahakColors.headingText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.history_empty_body), color = MaterialTheme.vahakColors.mutedText, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                }
            }
            else -> state.sessions
                .groupBy { DateFormat.getDateInstance(DateFormat.FULL).format(Date(it.startedAtEpochMillis)) }
                .forEach { (date, rides) ->
                    Text(date, color = MaterialTheme.vahakColors.mutedText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    rides.forEach { session ->
                        RideSessionCard(session, onClick = { onRideClick(session.id) }, onDelete = { deleteTarget = session })
                        Spacer(Modifier.height(12.dp))
                    }
                }
        }
    }
    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.ride_delete_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.ride_delete_message)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(session.id); deleteTarget = null }) { Text(androidx.compose.ui.res.stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(androidx.compose.ui.res.stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.vahakColors.card
        )
    }
}

@Composable
private fun RideSessionCard(session: RideSession, onClick: () -> Unit, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAtEpochMillis)), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                    Text(statusText(session.status), color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
                }
                if (session.status != RideSessionStatus.ACTIVE) {
                    TextButton(onClick = onDelete) { Text(androidx.compose.ui.res.stringResource(R.string.action_delete)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val distance = session.distanceMeters?.let { String.format(Locale.getDefault(), "%.1f km", it / 1000) }
                ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)
            val duration = formatDuration(session.durationMillis)
            Text(androidx.compose.ui.res.stringResource(R.string.ride_history_summary, distance, duration), color = MaterialTheme.vahakColors.mutedText, fontSize = 13.sp)
        }
    }
}

@Composable
fun RideDetailScreen(viewModel: RideHistoryViewModel, rideId: String, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) viewModel.reportSensorCsvPickerCancelled()
        else viewModel.exportSensorCsv(rideId, uri.toString())
    }
    LaunchedEffect(rideId) { viewModel.loadDetail(rideId) }
    AppScreen(modifier = modifier) {
        AppTopBar(title = androidx.compose.ui.res.stringResource(R.string.ride_detail_title), onBackClick = onBackClick)
        when {
            state.isDetailLoading -> Text(androidx.compose.ui.res.stringResource(R.string.ride_history_loading), color = MaterialTheme.vahakColors.mutedText)
            state.selectedSession == null -> Text(androidx.compose.ui.res.stringResource(R.string.ride_detail_unavailable), color = MaterialTheme.vahakColors.mutedText)
            else -> {
                val session = state.selectedSession!!
                Spacer(Modifier.height(12.dp))
                Text(DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(session.startedAtEpochMillis)), color = MaterialTheme.vahakColors.headingText, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Text(statusText(session.status), color = MaterialTheme.vahakColors.mutedText)
                Spacer(Modifier.height(12.dp))
                RecordedRideMap(state.selectedPoints.map { LatLng(it.latitude, it.longitude) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                RideMetricGrid(
                    listOf(
                        androidx.compose.ui.res.stringResource(R.string.ride_duration) to formatDuration(session.durationMillis),
                        androidx.compose.ui.res.stringResource(R.string.ride_distance) to (session.distanceMeters?.let { String.format(Locale.getDefault(), "%.2f km", it / 1000) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)),
                        androidx.compose.ui.res.stringResource(R.string.ride_average_speed) to (session.averageSpeedMetersPerSecond?.let { String.format(Locale.getDefault(), "%.1f km/h", it * 3.6) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)),
                        androidx.compose.ui.res.stringResource(R.string.ride_maximum_speed) to (session.maximumSpeedMetersPerSecond?.let { String.format(Locale.getDefault(), "%.1f km/h", it * 3.6) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable))
                    )
                )
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_title),
                            color = MaterialTheme.vahakColors.headingText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_description),
                            color = MaterialTheme.vahakColors.mutedText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        val isExporting = state.sensorCsvExportStatus == SensorCsvExportStatus.RUNNING
                        androidx.compose.material3.Button(
                            onClick = { exportLauncher.launch(SensorCsvExporter.suggestedFilename(rideId)) },
                            enabled = (state.sensorEventCount ?: 0L) > 0L && !isExporting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_action))
                        }
                        when {
                            state.sensorEventCount == null -> Text(
                                androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_count_unavailable),
                                color = MaterialTheme.vahakColors.mutedText,
                                fontSize = 12.sp
                            )
                            state.sensorEventCount == 0L -> Text(
                                androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_empty),
                                color = MaterialTheme.vahakColors.mutedText,
                                fontSize = 12.sp
                            )
                            isExporting -> {
                                val progress = state.sensorCsvProgress
                                if (progress != null) {
                                    val fraction = if (progress.totalRows > 0L) {
                                        (progress.writtenRows.toFloat() / progress.totalRows).coerceIn(0f, 1f)
                                    } else 0f
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        androidx.compose.ui.res.stringResource(
                                            R.string.ride_sensor_export_progress,
                                            progress.writtenRows,
                                            progress.totalRows
                                        ),
                                        color = MaterialTheme.vahakColors.mutedText,
                                        fontSize = 12.sp
                                    )
                                }
                                androidx.compose.material3.TextButton(onClick = viewModel::cancelSensorCsvExport) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_cancel))
                                }
                            }
                            state.sensorCsvExportStatus == SensorCsvExportStatus.SUCCEEDED -> Text(
                                androidx.compose.ui.res.stringResource(
                                    R.string.ride_sensor_export_success,
                                    state.sensorCsvProgress?.writtenRows ?: 0L
                                ),
                                color = MaterialTheme.vahakColors.primary,
                                fontSize = 12.sp
                            )
                            state.sensorCsvExportStatus == SensorCsvExportStatus.EMPTY -> Text(
                                androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_empty),
                                color = MaterialTheme.vahakColors.mutedText,
                                fontSize = 12.sp
                            )
                            state.sensorCsvExportStatus == SensorCsvExportStatus.FAILED -> Text(
                                listOfNotNull(
                                    androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_failed),
                                    state.sensorCsvError
                                ).joinToString(" "),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                            state.sensorCsvExportStatus == SensorCsvExportStatus.CANCELLED -> Text(
                                androidx.compose.ui.res.stringResource(R.string.ride_sensor_export_cancelled),
                                color = MaterialTheme.vahakColors.mutedText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                if (state.selectedPoints.isEmpty()) Text(androidx.compose.ui.res.stringResource(R.string.ride_no_gps_points), color = MaterialTheme.vahakColors.mutedText)
            }
        }
    }
}

@Composable
fun RideCompletionScreen(
    viewModel: RideHistoryViewModel,
    onHome: () -> Unit,
    onDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.sessions.firstOrNull()
    var points by remember(session?.id) { mutableStateOf(emptyList<com.manish.vahakvigil.domain.model.RideSessionPoint>()) }
    LaunchedEffect(session?.id) {
        points = session?.let { com.manish.vahakvigil.domain.location.RideRouteReplay.filterReliablePoints(viewModel.getPoints(it.id)) }.orEmpty()
    }
    AppScreen(modifier = modifier) {
        AppTopBar(title = androidx.compose.ui.res.stringResource(R.string.ride_completion_title))
        Spacer(Modifier.height(12.dp))
        when {
            state.isLoading || session == null -> Text(androidx.compose.ui.res.stringResource(R.string.ride_history_loading), color = MaterialTheme.vahakColors.mutedText)
            else -> {
                val summary = com.manish.vahakvigil.domain.model.RideCompletionSummary.from(session, points.size)
                Text(
                    androidx.compose.ui.res.stringResource(if (!summary.isFinalized) R.string.ride_completion_saving else R.string.ride_completion_heading),
                    color = MaterialTheme.vahakColors.headingText,
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(statusText(session.status), color = MaterialTheme.vahakColors.mutedText)
                Spacer(Modifier.height(14.dp))
                RecordedRideMap(points.map { LatLng(it.latitude, it.longitude) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                RideMetricGrid(
                    listOf(
                        androidx.compose.ui.res.stringResource(R.string.ride_duration) to formatDuration(session.durationMillis),
                        androidx.compose.ui.res.stringResource(R.string.ride_distance) to (session.distanceMeters?.let { String.format(Locale.getDefault(), "%.2f km", it / 1000) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)),
                        androidx.compose.ui.res.stringResource(R.string.ride_average_speed) to (session.averageSpeedMetersPerSecond?.let { String.format(Locale.getDefault(), "%.1f km/h", it * 3.6) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)),
                        androidx.compose.ui.res.stringResource(R.string.ride_maximum_speed) to (session.maximumSpeedMetersPerSecond?.let { String.format(Locale.getDefault(), "%.1f km/h", it * 3.6) } ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable))
                    )
                )
                val start = session.startLatitude?.let { lat -> session.startLongitude?.let { lon -> String.format(Locale.getDefault(), "%.5f, %.5f", lat, lon) } }
                val end = session.endLatitude?.let { lat -> session.endLongitude?.let { lon -> String.format(Locale.getDefault(), "%.5f, %.5f", lat, lon) } }
                RideMetricGrid(
                    listOf(
                        androidx.compose.ui.res.stringResource(R.string.ride_completion_start) to (start ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable)),
                        androidx.compose.ui.res.stringResource(R.string.ride_completion_end) to (end ?: androidx.compose.ui.res.stringResource(R.string.ride_metric_unavailable))
                    )
                )
                if (summary.hasIncompleteGps) {
                    Spacer(Modifier.height(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.ride_completion_incomplete), color = MaterialTheme.vahakColors.mutedText)
                }
                Spacer(Modifier.height(18.dp))
                androidx.compose.material3.Button(onClick = { onDetails(session.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ride_completion_details))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(R.string.ride_completion_home))
        }
    }
}

@Composable
private fun RecordedRideMap(points: List<LatLng>, modifier: Modifier) {
    val context = LocalContext.current
    val configured = remember(context) { GoogleMapsConfiguration.isApiKeyConfigured(context) }
    if (!configured) {
        AppCard(modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.maps_configuration_required), color = MaterialTheme.vahakColors.headingText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.maps_configuration_hint), color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp, lineHeight = 16.sp)
                if (points.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val first = points.first()
                    val last = points.last()
                    Text(
                        androidx.compose.ui.res.stringResource(
                            R.string.maps_recorded_location_summary,
                            String.format(Locale.US, "%.5f, %.5f", first.latitude, first.longitude),
                            String.format(Locale.US, "%.5f, %.5f", last.latitude, last.longitude)
                        ),
                        color = MaterialTheme.vahakColors.primary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.ride_no_gps_points), color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp)
                }
            }
        }
        return
    }
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
            Text(androidx.compose.ui.res.stringResource(R.string.ride_no_gps_route_points), color = MaterialTheme.vahakColors.mutedText)
        }
        return
    }
    ActualRecordedRideMap(points, modifier.fillMaxWidth().height(230.dp))
}

@Composable
private fun RideMetricGrid(items: List<Pair<String, String>>) {
    AppCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, color = MaterialTheme.vahakColors.mutedText, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(value, color = MaterialTheme.vahakColors.headingText, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun statusText(status: RideSessionStatus): String = androidx.compose.ui.res.stringResource(
    when (status) {
        RideSessionStatus.ACTIVE -> R.string.ride_status_active
        RideSessionStatus.COMPLETED -> R.string.ride_status_completed
        RideSessionStatus.INTERRUPTED -> R.string.ride_status_interrupted
        RideSessionStatus.INCOMPLETE -> R.string.ride_status_incomplete
    }
)

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun RideHistoryScreenPreview() {
    VahakVigilTheme { VahakVigilGradientBackground { AppScreen { _ -> Text("Ride history preview") } } }
}
