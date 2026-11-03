package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.RideRoutePlan
import com.manish.vahakvigil.domain.model.SafetyOverlayFeature

interface DestinationSearchRepository {
    suspend fun search(query: String): List<RideDestination>
}

interface RideRoutingRepository {
    suspend fun getRoute(origin: EmergencyLocation.Available, destination: RideDestination): RideRoutePlan
}

/** Provider contracts for verified overlays. Empty results mean unavailable, never clear/no-risk. */
interface TrafficDataProvider {
    suspend fun trafficFor(route: RideRoutePlan): List<SafetyOverlayFeature>?
}

interface AccidentRiskDataProvider {
    suspend fun riskFor(route: RideRoutePlan): List<SafetyOverlayFeature>?
}

class EmptyDestinationSearchRepository : DestinationSearchRepository {
    override suspend fun search(query: String): List<RideDestination> = emptyList()
}

class UnconfiguredRideRoutingRepository : RideRoutingRepository {
    override suspend fun getRoute(
        origin: EmergencyLocation.Available,
        destination: RideDestination
    ): RideRoutePlan = throw RoutingUnavailableException()
}

class RoutingUnavailableException : IllegalStateException("No route provider is configured")

class UnavailableTrafficDataProvider : TrafficDataProvider {
    override suspend fun trafficFor(route: RideRoutePlan): List<SafetyOverlayFeature>? = null
}

class UnavailableAccidentRiskDataProvider : AccidentRiskDataProvider {
    override suspend fun riskFor(route: RideRoutePlan): List<SafetyOverlayFeature>? = null
}
