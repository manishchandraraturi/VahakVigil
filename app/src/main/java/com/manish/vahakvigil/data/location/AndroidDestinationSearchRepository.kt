package com.manish.vahakvigil.data.location

import android.content.Context
import android.location.Geocoder
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.repository.DestinationSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.Locale

/** Uses Android's platform geocoder; it does not add or call a billed Google Places API. */
class AndroidDestinationSearchRepository(context: Context) : DestinationSearchRepository {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun search(query: String): List<RideDestination> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext emptyList()
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val addresses = try {
            geocoder.getFromLocationName(query.trim(), MAX_RESULTS).orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
        addresses.mapNotNull { address ->
            val latitude = address.latitude
            val longitude = address.longitude
            val label = address.getAddressLine(0)?.takeIf(String::isNotBlank)
                ?: address.featureName?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            if (!EmergencyLocation.isValidCoordinate(latitude, longitude)) return@mapNotNull null
            RideDestination(
                id = "${latitude.toBits()}:${longitude.toBits()}",
                label = label,
                latitude = latitude,
                longitude = longitude
            )
        }.distinctBy(RideDestination::id)
    }

    private companion object { const val MAX_RESULTS = 6 }
}
