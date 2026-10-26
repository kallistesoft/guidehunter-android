package ca.kallistesoft.guidehunter.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A place on Earth, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    /** Great-circle (haversine) distance to [other], in kilometres: how far a trip is, as the crow flies. */
    fun distanceKm(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6_371.0

        /** Where the map opens when the user's location isn't known: southern Quebec. */
        val DEFAULT_CENTER = GeoPoint(46.8, -71.2)
    }
}
