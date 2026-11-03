package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.GeoPoint
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.model.RideRoutePlan
import com.manish.vahakvigil.domain.model.RideRouteState
import com.manish.vahakvigil.domain.repository.DestinationSearchRepository
import com.manish.vahakvigil.domain.repository.RideRoutingRepository
import com.manish.vahakvigil.domain.repository.RoutingUnavailableException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePlanningCoordinatorTest {
    private val destination = RideDestination("dest", "Central Station", 12.98, 77.60)
    private val origin = RideLocationState.Available(12.97, 77.59, 7f, 1000L, 4f)
    private val route = RideRoutePlan(listOf(GeoPoint(12.97, 77.59), GeoPoint(12.98, 77.60)), 1500.0, 600L)

    @Test fun `destination can be selected then cleared`() {
        val coordinator = coordinator()
        coordinator.selectDestination(destination)
        assertEquals(destination, coordinator.state.value.destination)
        coordinator.clearDestination()
        assertNull(coordinator.state.value.destination)
        assertEquals(RideRouteState.NotRequested, coordinator.state.value.routeState)
    }

    @Test fun `route moves through loading to available`() = runTest {
        val coordinator = coordinator(routeProvider = object : RideRoutingRepository {
            override suspend fun getRoute(origin: EmergencyLocation.Available, destination: RideDestination) = route
        })
        coordinator.selectDestination(destination)
        coordinator.onRideStarted()
        val request = coordinator.onLocationState(origin)!!
        assertEquals(RideRouteState.Loading, coordinator.state.value.routeState)
        coordinator.loadRoute(request)
        assertEquals(RideRouteState.Available(route), coordinator.state.value.routeState)
    }

    @Test fun `route provider failure is surfaced without fabricated route`() = runTest {
        val coordinator = coordinator(routeProvider = object : RideRoutingRepository {
            override suspend fun getRoute(origin: EmergencyLocation.Available, destination: RideDestination): RideRoutePlan = throw RoutingUnavailableException()
        })
        coordinator.selectDestination(destination)
        coordinator.onRideStarted()
        coordinator.onLocationState(origin)?.let { coordinator.loadRoute(it) }
        assertEquals(RideRouteState.Unavailable, coordinator.state.value.routeState)
    }

    @Test fun `permission and disabled GPS states propagate during active ride`() {
        val coordinator = coordinator()
        coordinator.onRideStarted()
        coordinator.onLocationState(RideLocationState.PermissionDenied)
        assertEquals(RideLocationState.PermissionDenied, coordinator.state.value.locationState)
        coordinator.onLocationState(RideLocationState.GpsDisabled)
        assertEquals(RideLocationState.GpsDisabled, coordinator.state.value.locationState)
    }

    @Test fun `active ride accepts location updates and recovers after temporary loss`() {
        val coordinator = coordinator()
        coordinator.onRideStarted()
        coordinator.onLocationState(origin)
        coordinator.onLocationState(RideLocationState.LocationTemporarilyUnavailable)
        assertEquals(RideLocationState.LocationTemporarilyUnavailable, coordinator.state.value.locationState)
        assertEquals(origin, coordinator.state.value.currentLocation)
        val recovered = origin.copy(latitude = 12.971, timestampMs = 2000L)
        coordinator.onLocationState(recovered)
        assertEquals(recovered, coordinator.state.value.currentLocation)
        assertEquals(recovered, coordinator.state.value.locationState)
    }

    @Test fun `ride can start without destination and stopping clears route and live location`() = runTest {
        val coordinator = coordinator()
        assertNull(coordinator.onRideStarted())
        assertTrue(coordinator.state.value.isRideActive)
        assertEquals(RideRouteState.NotRequested, coordinator.state.value.routeState)
        coordinator.selectDestination(destination)
        val request = coordinator.onLocationState(origin)!!
        coordinator.onRideStopped()
        coordinator.loadRoute(request)
        assertEquals(RideRouteState.NotRequested, coordinator.state.value.routeState)
        assertNull(coordinator.state.value.currentLocation)
        assertEquals(RideLocationState.Inactive, coordinator.state.value.locationState)
        assertEquals(destination, coordinator.state.value.destination)
    }

    private fun coordinator(routeProvider: RideRoutingRepository = object : RideRoutingRepository {
        override suspend fun getRoute(origin: EmergencyLocation.Available, destination: RideDestination) = route
    }) = RidePlanningCoordinator(
        searchRepository = object : DestinationSearchRepository {
            override suspend fun search(query: String) = listOf(destination)
        },
        routingRepository = routeProvider
    )
}
