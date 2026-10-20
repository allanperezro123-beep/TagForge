package com.tagforge.app.infrastructure.android

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.tagforge.domain.location.CoordinateText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One match for a typed address or place name. Coordinates are already in the text form the record stores. */
data class PlaceResult(val label: String, val latitude: String, val longitude: String)

sealed interface PlaceSearchResult {
    data class Found(val places: List<PlaceResult>) : PlaceSearchResult
    data object NoMatches : PlaceSearchResult

    /** The phone has no geocoding service (common without Google services): the screen offers the other ways instead. */
    data object NoBackend : PlaceSearchResult

    /** The lookup could not complete, usually because there is no connection. */
    data object Failed : PlaceSearchResult
}

/** Address and place search through Android's own [Geocoder]: no extra library and no Google Play Services dependency. */
class PlaceSearch(private val context: Context, private val io: CoroutineDispatcher) {

    suspend fun search(query: String): PlaceSearchResult {
        val text = query.trim()
        if (text.isEmpty()) return PlaceSearchResult.NoMatches
        if (!Geocoder.isPresent()) return PlaceSearchResult.NoBackend
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses = lookup(geocoder, text)
            val places = addresses.mapNotNull(::toPlace).distinctBy { it.label }
            if (places.isEmpty()) PlaceSearchResult.NoMatches else PlaceSearchResult.Found(places)
        } catch (_: IOException) {
            PlaceSearchResult.Failed
        } catch (_: IllegalArgumentException) {
            PlaceSearchResult.NoMatches
        }
    }

    private suspend fun lookup(geocoder: Geocoder, text: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine<List<Address>> { continuation ->
                geocoder.getFromLocationName(
                    text,
                    MAX_RESULTS,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)

                        override fun onError(errorMessage: String?) = continuation.resumeWithException(IOException(errorMessage))
                    },
                )
            }
        } else {
            withContext(io) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(text, MAX_RESULTS).orEmpty()
            }
        }

    private fun toPlace(address: Address): PlaceResult? {
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val lines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it)?.takeIf(String::isNotBlank) }
        val label = lines.joinToString(", ").ifBlank { address.featureName ?: address.locality ?: return null }
        return PlaceResult(label, CoordinateText.format(address.latitude), CoordinateText.format(address.longitude))
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
