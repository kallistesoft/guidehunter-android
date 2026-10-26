package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {
    @Test
    fun `distance is the great-circle distance in kilometres`() {
        val quebec = GeoPoint(46.81, -71.21)
        val montreal = GeoPoint(45.50, -73.57)
        assertEquals(233.0, quebec.distanceKm(montreal), 3.0)
        assertEquals(quebec.distanceKm(montreal), montreal.distanceKm(quebec), 1e-9)
        assertEquals(0.0, quebec.distanceKm(quebec), 1e-9)
    }

    @Test
    fun `Paris to Quebec is across the ocean`() {
        assertEquals(5_300.0, GeoPoint(48.86, 2.35).distanceKm(GeoPoint(46.81, -71.21)), 100.0)
    }
}
