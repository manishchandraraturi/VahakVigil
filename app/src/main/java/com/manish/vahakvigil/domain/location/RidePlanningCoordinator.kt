package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.model.RidePlanningState
import com.manish.vahakvigil.domain.model.RideRouteState
import com.manish.vahakvigil.domain.repository.DestinationSearchRepository
import com.manish.vahakvigil.domain.repository.RideRoutingRepository
import com.manish.vahakvigil.domain.repository.RoutingUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException

/** Destination and routing state, independent from crash detection and emergency dispatch. */
class RidePlanningCoordinator(
    private val searchRepository: DestinationSearchRepository,
    private val routingRepository: RideRoutingRepository
) {
    private val _state = MutableStateFlow(RidePlanningState())
    val state: StateFlow<RidePlanningState> = _state.asStateFlow()
    private var routeRequestVersion = 0L
    private var searchRequestVersion = 0L

    fun setSearchQuery(query: String) {
        searchRequestVersion++
        _state.update { it.copy(searchQuery = query, searchResults = emptyList(), searchFailed = false, isSearching = false) }
    }

    suspend fun searchDestinations(query: String) {
        val normalized = query.trim()
        setSearchQuery(query)
        if (normalized.isEmpty()) return
        val requestVersion = ++searchRequestVersion
        _state.update { it.copy(isSearching = true) }
        try {
            val results = searchRepository.search(normalized)
            if (requestVersion == searchRequestVersion) {
                _state.update { it.copy(isSearching = false, searchResults = results, searchFailed = false) }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (requestVersion == searchRequestVersion) {
                _state.update { it.copy(isSearching = false, searchResults = emptyList(), searchFailed = true) }
            }
        }
    }

    fun selectDestination(destination: RideDestination): RouteRequest? {
        routeRequestVersion++
        _state.update {
            it.copy(
                destination = destination,
                searchResults = emptyList(),
                routeState = if (it.isRideActive) RideRouteState.WaitingForLocation else RideRouteState.NotRequested
            )
        }
        return beginRouteRequestIfReady()
    }

    fun clearDestination() {
        routeRequestVersion++
        _state.update { it.copy(destination = null, searchResults = emptyList(), routeState = RideRouteState.NotRequested) }
    }

    fun onRideStarted(): RouteRequest? {
        _state.update {
            it.copy(
                isRideActive = true,
                locationState = RideLocationState.WaitingForLocation,
                currentLocation = null,
                routeState = if (it.destination == null) RideRouteState.NotRequested else RideRouteState.WaitingForLocation
            )
        }
        return beginRouteRequestIfReady()
    }

    fun onRideStopped() {
        routeRequestVersion++
        _state.update {
            it.copy(
                isRideActive = false,
                locationState = RideLocationState.Inactive,
                currentLocation = null,
                routeState = RideRouteState.NotRequested
            )
        }
    }

    fun onLocationState(locationState: RideLocationState): RouteRequest? {
        if (!_state.value.isRideActive) return null
        _state.update { current ->
            current.copy(
                locationState = locationState,
                currentLocation = if (locationState is RideLocationState.Available) locationState else current.currentLocation
            )
        }
        return if (locationState is RideLocationState.Available) beginRouteRequestIfReady() else null
    }

    fun beginRouteRequestIfReady(): RouteRequest? {
        val current = _state.value
        val origin = current.currentLocation ?: return null
        val destination = current.destination ?: return null
        if (!current.isRideActive) return null
        if (current.routeState !is RideRouteState.WaitingForLocation) return null

        val version = ++routeRequestVersion
        _state.update { it.copy(routeState = RideRouteState.Loading) }
        return RouteRequest(version, origin, destination)
    }

    suspend fun loadRoute(request: RouteRequest) {
        val routeState = try {
            RideRouteState.Available(routingRepository.getRoute(request.origin.toEmergencyLocation(), request.destination))
        } catch (_: RoutingUnavailableException) {
            RideRouteState.Unavailable
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            RideRouteState.Failed
        }
        if (request.version == routeRequestVersion && _state.value.isRideActive && _state.value.destination == request.destination) {
            _state.update { it.copy(routeState = routeState) }
        }
    }

    private fun RideLocationState.Available.toEmergencyLocation() = EmergencyLocation.Available(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        timestampMs = timestampMs
    )

    data class RouteRequest internal constructor(
        val version: Long,
        val origin: RideLocationState.Available,
        val destination: RideDestination
    )
}
