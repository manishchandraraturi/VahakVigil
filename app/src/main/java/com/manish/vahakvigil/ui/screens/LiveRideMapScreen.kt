package com.manish.vahakvigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.ui.components.ActualLiveRideMap
import com.manish.vahakvigil.ui.components.AppScreen
import com.manish.vahakvigil.ui.components.AppTopBar
import com.manish.vahakvigil.ui.components.GoogleMapsConfiguration
import com.manish.vahakvigil.ui.theme.vahakColors

@Composable
fun LiveRideMapScreen(
    uiState: com.manish.vahakvigil.ui.screens.home.HomeUiState,
    onBackClick: () -> Unit,
    onDestinationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val location = uiState.ridePlanning.currentLocation
    val statusText = when (uiState.rideLocationState) {
        RideLocationState.PermissionDenied -> stringResource(R.string.ride_status_permission_required)
        RideLocationState.GpsDisabled -> stringResource(R.string.ride_status_gps_disabled)
        RideLocationState.LocationUnavailable -> stringResource(R.string.ride_status_location_unavailable)
        RideLocationState.LocationTemporarilyUnavailable -> stringResource(R.string.ride_status_signal_lost)
        RideLocationState.WaitingForLocation, RideLocationState.Inactive -> stringResource(R.string.ride_status_acquiring)
        is RideLocationState.Available -> location?.accuracyMeters?.let { context.getString(R.string.home_gps_accuracy_value, it) }
            ?: stringResource(R.string.ride_status_location_available)
    }
    AppScreen(modifier = modifier) {
        AppTopBar(title = stringResource(R.string.home_live_ride_map), onBackClick = onBackClick)
        if (GoogleMapsConfiguration.isApiKeyConfigured(context)) {
            Box(Modifier.fillMaxWidth().height(560.dp).padding(top = 12.dp)) {
                if (location != null || uiState.ridePlanning.destination != null || uiState.ridePath.isNotEmpty()) {
                    ActualLiveRideMap(
                        currentLocation = location,
                        destination = uiState.ridePlanning.destination,
                        ridePath = uiState.ridePath,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    androidx.compose.material3.Surface(
                        Modifier.align(Alignment.Center).padding(20.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Text(stringResource(R.string.ride_map_waiting_for_position), Modifier.padding(16.dp), color = MaterialTheme.vahakColors.mutedText)
                    }
                }
                Column(
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(statusText, color = MaterialTheme.vahakColors.headingText, fontSize = 13.sp)
                            location?.accuracyMeters?.let {
                                Text(context.getString(R.string.home_gps_accuracy_value, it), color = MaterialTheme.vahakColors.mutedText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            androidx.compose.material3.OutlinedButton(onClick = onDestinationClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ride_edit_destination))
            }
        } else {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.maps_configuration_required), color = MaterialTheme.vahakColors.headingText)
                    Text(stringResource(R.string.maps_configuration_hint), color = MaterialTheme.vahakColors.mutedText)
                    Text(statusText, color = MaterialTheme.vahakColors.primary)
                    location?.let {
                        Text(String.format(java.util.Locale.US, "%.5f°, %.5f°", it.latitude, it.longitude), color = MaterialTheme.vahakColors.mutedText)
                    }
                }
            }
        }
    }
}
