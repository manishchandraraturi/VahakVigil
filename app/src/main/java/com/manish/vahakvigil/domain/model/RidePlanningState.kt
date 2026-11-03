package com.manish.vahakvigil.domain.model


sealed interface RideRouteState {
    data object NotRequested : RideRouteState
    data object WaitingForLocation : RideRouteState
    data object Loading : RideRouteState
    data class Available(val plan: RideRoutePlan) : RideRouteState
    data object Unavailable : RideRouteState
    data object Failed : RideRouteState
}

data class RidePlanningState(
    val destination: RideDestination? = null,
    val searchQuery: String = "",
    val searchResults: List<RideDestination> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val locationState: RideLocationState = RideLocationState.Inactive,
    val currentLocation: RideLocationState.Available? = null,
    val routeState: RideRouteState = RideRouteState.NotRequested,
    val trafficFeatures: List<SafetyOverlayFeature>? = null,
    val accidentRiskFeatures: List<SafetyOverlayFeature>? = null,
    val isRideActive: Boolean = false
)
