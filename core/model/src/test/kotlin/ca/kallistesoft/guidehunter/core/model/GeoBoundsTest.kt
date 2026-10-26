package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoBoundsTest {
    @Test
    fun `southern Quebec holds Quebec City, not Toronto nor the north`() {
        val southernQuebec = GeoBounds(GeoPoint(45.0, -80.0), GeoPoint(50.0, -64.0))
        assertTrue(GeoPoint(46.81, -71.21) in southernQuebec)
        assertTrue("the edges are inside", GeoPoint(45.0, -64.0) in southernQuebec)
        assertFalse(GeoPoint(43.65, -79.38) in southernQuebec)
        assertFalse(GeoPoint(54.8, -66.8) in southernQuebec)
        assertFalse(GeoPoint(48.0, -60.0) in southernQuebec)
    }

    @Test
    fun `a view across the 180th meridian wraps around`() {
        val pacific = GeoBounds(GeoPoint(50.0, 170.0), GeoPoint(70.0, -160.0))
        assertTrue(GeoPoint(60.0, 175.0) in pacific)
        assertTrue(GeoPoint(60.0, -170.0) in pacific)
        assertFalse(GeoPoint(60.0, 0.0) in pacific)
        assertFalse(GeoPoint(40.0, 175.0) in pacific)
    }
}
