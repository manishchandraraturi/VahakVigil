package com.manish.vahakvigil.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.manish.vahakvigil.R
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.ui.theme.vahakColors
import com.manish.vahakvigil.ui.theme.DarkAccentBlue
import kotlinx.coroutines.launch

object GoogleMapsConfiguration {
    fun hasApiKey(value: String?): Boolean = !value.isNullOrBlank()

    fun isApiKeyConfigured(context: Context): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        hasApiKey(info.metaData?.getString("com.google.android.geo.API_KEY"))
    }.getOrDefault(false)
}

@Composable
fun ActualLiveRideMap(
    currentLocation: RideLocationState.Available?,
    destination: RideDestination?,
    ridePath: List<RideLocationPoint>,
    modifier: Modifier = Modifier,
    allowRotation: Boolean = true
) {
    val context = LocalContext.current
    val initialTarget = currentLocation?.let { LatLng(it.latitude, it.longitude) }
        ?: ridePath.lastOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: destination?.let { LatLng(it.latitude, it.longitude) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget ?: LatLng(0.0, 0.0), 14f)
    }
    val scope = rememberCoroutineScope()
    val riderMarkerState = remember { MarkerState(initialTarget ?: LatLng(0.0, 0.0)) }
    val followController = remember { MapFollowController() }
    var followRider by remember { mutableStateOf(followController.isFollowing) }
    var initialFitDone by remember { mutableStateOf(false) }
    var fittedDestinationId by remember { mutableStateOf(destination?.id) }
    val path = remember(ridePath) { ridePath.map { LatLng(it.latitude, it.longitude) } }
    val destinationPoint = destination?.let { LatLng(it.latitude, it.longitude) }
    val isDarkTheme = MaterialTheme.colorScheme.primary == DarkAccentBlue
    val mapStyleJson = stringResource(if (isDarkTheme) R.string.google_map_style_dark else R.string.google_map_style_light)

    LaunchedEffect(currentLocation?.timestampMs) {
        currentLocation?.let { riderMarkerState.position = LatLng(it.latitude, it.longitude) }
    }

    LaunchedEffect(camera.isMoving, camera.cameraMoveStartedReason) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            followController.onGestureStarted()
            followRider = false
        }
    }

    LaunchedEffect(destination?.id, currentLocation?.timestampMs) {
        val riderPoint = currentLocation?.let { LatLng(it.latitude, it.longitude) }
        val destinationChanged = fittedDestinationId != destination?.id
        if ((!initialFitDone || destinationChanged) && riderPoint != null) {
            val points = listOfNotNull(riderPoint, destinationPoint)
            if (points.size > 1 && points[0] != points[1]) {
                runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(boundsOf(points), 72)) }
            } else {
                camera.animate(CameraUpdateFactory.newLatLngZoom(riderPoint, 15f))
            }
            initialFitDone = true
            fittedDestinationId = destination?.id
        } else if (riderPoint != null && followRider && followController.shouldFollowNewFix(hasFix = true)) {
            // Pan to the real fix only. No fabricated intermediate coordinate is introduced.
            camera.animate(CameraUpdateFactory.newLatLng(riderPoint))
        } else if ((!initialFitDone || destinationChanged) && destinationPoint != null) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(destinationPoint, 14f))
            initialFitDone = true
            fittedDestinationId = destination?.id
        }
    }

    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            // The app's live location flow is the only location source. Avoid enabling
            // the Maps SDK my-location layer, which would create a second subscription.
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                rotationGesturesEnabled = allowRotation,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true,
                tiltGesturesEnabled = true
            ),
            contentDescription = stringResource(R.string.ride_map_content_description)
        ) {
            MapEffect(mapStyleJson) { map ->
                val style = runCatching { com.google.android.gms.maps.model.MapStyleOptions(mapStyleJson) }.getOrNull()
                map.setMapStyle(style)
            }
            if (path.size > 1) Polyline(points = path, color = Color(0xFF008FCB), width = 7f)
            currentLocation?.let { fix ->
                val riderPosition = LatLng(fix.latitude, fix.longitude)
                fix.accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.let { accuracy ->
                    Circle(
                        center = riderPosition,
                        radius = accuracy.toDouble(),
                        strokeColor = Color(0xFF1976D2).copy(alpha = 0.7f),
                        fillColor = Color(0xFF1976D2).copy(alpha = 0.14f),
                        strokeWidth = 1.5f
                    )
                }
                Marker(
                    state = riderMarkerState,
                    title = stringResource(R.string.ride_marker_current_location),
                    snippet = fix.accuracyMeters?.let { context.getString(R.string.home_gps_accuracy_value, it) },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            destination?.let { target ->
                Marker(
                    state = MarkerState(LatLng(target.latitude, target.longitude)),
                    title = target.label,
                    snippet = stringResource(R.string.ride_marker_destination)
                )
            }
        }
        if (!followRider && currentLocation != null) {
            MapOverlayButton(
                label = stringResource(R.string.ride_recenter),
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = {
                    followController.onRecenterPressed()
                    followRider = true
                    scope.launch {
                        camera.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                camera.position.zoom.coerceAtLeast(14f)
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun MapOverlayButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label) }
}

@Composable
fun ActualRecordedRideMap(points: List<LatLng>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.vahakColors
    val mapStyleJson = stringResource(if (MaterialTheme.colorScheme.primary == DarkAccentBlue) R.string.google_map_style_dark else R.string.google_map_style_light)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.firstOrNull() ?: LatLng(0.0, 0.0), 14f)
    }
    LaunchedEffect(points) {
        when (points.size) {
            0 -> Unit
            1 -> camera.animate(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
            else -> runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(boundsOf(points), 56)) }
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = true)
    ) {
        MapEffect(mapStyleJson) { map ->
            val style = runCatching { com.google.android.gms.maps.model.MapStyleOptions(mapStyleJson) }.getOrNull()
            map.setMapStyle(style)
        }
        if (points.size > 1) Polyline(points, color = colors.primary, width = 8f)
        points.firstOrNull()?.let { Marker(MarkerState(it), title = stringResource(R.string.ride_start_marker)) }
        points.lastOrNull()?.takeIf { points.size > 1 }?.let {
            Marker(MarkerState(it), title = stringResource(R.string.ride_end_marker))
        }
    }
}

private fun boundsOf(points: List<LatLng>): LatLngBounds = LatLngBounds.builder().apply {
    points.forEach(::include)
}.build()
