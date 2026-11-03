package com.manish.vahakvigil.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.model.RidePlanningState
import com.manish.vahakvigil.domain.model.RideRouteState
import com.manish.vahakvigil.domain.model.RideRoutePlan
import com.manish.vahakvigil.ui.theme.vahakColors
import java.util.Locale

@Composable
fun LiveRideMapCard(
    planningState: RidePlanningState,
    ridePath: List<RideLocationPoint>,
    isMonitoring: Boolean,
    onRequestPermissionClick: () -> Unit = {},
    onEditDestinationClick: () -> Unit = {},
    onOpenFullMapClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.vahakColors
    val context = LocalContext.current
    val current = planningState.currentLocation
    val status = when (planningState.locationState) {
        RideLocationState.PermissionDenied -> context.getString(com.manish.vahakvigil.R.string.ride_status_permission_required)
        RideLocationState.GpsDisabled -> context.getString(com.manish.vahakvigil.R.string.ride_status_gps_disabled)
        RideLocationState.LocationUnavailable -> context.getString(com.manish.vahakvigil.R.string.ride_status_location_unavailable)
        RideLocationState.LocationTemporarilyUnavailable -> context.getString(com.manish.vahakvigil.R.string.ride_status_signal_lost)
        RideLocationState.WaitingForLocation -> context.getString(com.manish.vahakvigil.R.string.ride_status_acquiring)
        RideLocationState.Inactive -> context.getString(if (isMonitoring) com.manish.vahakvigil.R.string.ride_status_acquiring else com.manish.vahakvigil.R.string.ride_status_standby)
        is RideLocationState.Available -> context.getString(com.manish.vahakvigil.R.string.ride_status_location_available)
    }
    val mapKeyConfigured = remember(context) { GoogleMapsConfiguration.isApiKeyConfigured(context) }

    AppCard(modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(context.getString(com.manish.vahakvigil.R.string.home_live_ride_map), color = colors.headingText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = if (planningState.locationState is RideLocationState.Available) colors.primary else colors.mutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onEditDestinationClick, shape = RoundedCornerShape(16.dp)) {
                    Text(context.getString(if (planningState.destination == null) com.manish.vahakvigil.R.string.ride_add_destination else com.manish.vahakvigil.R.string.ride_edit_destination), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (mapKeyConfigured) {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))) {
                    if (current != null || planningState.destination != null || ridePath.isNotEmpty()) {
                        ActualLiveRideMap(
                            currentLocation = current,
                            destination = planningState.destination,
                            ridePath = ridePath,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(context.getString(com.manish.vahakvigil.R.string.ride_map_waiting_for_position), color = colors.mutedText, fontSize = 12.sp)
                        }
                    }
                }
                TextButton(onClick = onOpenFullMapClick, modifier = Modifier.align(Alignment.End)) {
                    Text(context.getString(com.manish.vahakvigil.R.string.ride_open_full_map))
                }
            } else {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.CenterStart) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(context.getString(com.manish.vahakvigil.R.string.maps_configuration_required), color = colors.headingText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(context.getString(com.manish.vahakvigil.R.string.maps_configuration_hint), color = colors.mutedText, fontSize = 11.sp, lineHeight = 15.sp)
                        current?.let { fix ->
                            Spacer(Modifier.height(4.dp))
                            Text(String.format(Locale.US, "%.5f°, %.5f°", fix.latitude, fix.longitude), color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            if (planningState.locationState is RideLocationState.PermissionDenied) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestPermissionClick) { Text(context.getString(com.manish.vahakvigil.R.string.ride_grant_location)) }
            }
            planningState.destination?.let { destination ->
                Spacer(Modifier.height(8.dp))
                Text(context.getString(com.manish.vahakvigil.R.string.ride_destination_label, destination.label), color = colors.headingText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            when (planningState.routeState) {
                is RideRouteState.Available -> RouteMetrics((planningState.routeState as RideRouteState.Available).plan)
                RideRouteState.Loading, RideRouteState.WaitingForLocation -> Text(context.getString(com.manish.vahakvigil.R.string.ride_route_loading), color = colors.mutedText, fontSize = 12.sp)
                RideRouteState.Unavailable -> Text(context.getString(com.manish.vahakvigil.R.string.ride_route_unavailable), color = colors.mutedText, fontSize = 12.sp)
                RideRouteState.Failed -> Text(context.getString(com.manish.vahakvigil.R.string.ride_route_failed), color = colors.mutedText, fontSize = 12.sp)
                RideRouteState.NotRequested -> if (planningState.destination == null) Text(context.getString(com.manish.vahakvigil.R.string.ride_no_destination), color = colors.mutedText, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(context.getString(com.manish.vahakvigil.R.string.ride_traffic_unavailable), color = colors.mutedText, fontSize = 11.sp)
            Text(context.getString(com.manish.vahakvigil.R.string.ride_risk_unavailable), color = colors.mutedText, fontSize = 11.sp)
            if (current != null && mapKeyConfigured) {
                Spacer(Modifier.height(6.dp))
                val speed = current.speedMps?.let { String.format(Locale.US, " · %.0f km/h", it * 3.6f) }.orEmpty()
                Text(String.format(Locale.US, "%.5f°, %.5f°%s", current.latitude, current.longitude, speed), color = colors.mutedText, fontSize = 11.sp)
            } else if (planningState.locationState is RideLocationState.LocationTemporarilyUnavailable) {
                Text(context.getString(com.manish.vahakvigil.R.string.ride_last_known_position), color = colors.mutedText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RouteMetrics(plan: RideRoutePlan) {
    val colors = MaterialTheme.vahakColors
    val distance = if (plan.totalDistanceMeters >= 1000) String.format(Locale.US, "%.1f km", plan.totalDistanceMeters / 1000) else String.format(Locale.US, "%.0f m", plan.totalDistanceMeters)
    val duration = plan.estimatedDurationSeconds.let { "${it / 60} min" }
    Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_route_summary, distance, duration), color = colors.headingText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    plan.remainingDistanceMeters?.let { Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_remaining_distance, String.format(Locale.US, "%.1f km", it / 1000)), color = colors.mutedText, fontSize = 12.sp) }
    plan.estimatedArrivalEpochMillis?.let { epoch ->
        val formatted = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(epoch))
        Text(androidx.compose.ui.res.stringResource(com.manish.vahakvigil.R.string.ride_estimated_arrival, formatted), color = colors.mutedText, fontSize = 12.sp)
    }
}
