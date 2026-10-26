package ca.kallistesoft.guidehunter.feature.becomeguide

import ca.kallistesoft.guidehunter.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {
    @Test
    fun `decimal degrees, with a point or a comma, within the world`() {
        assertEquals(GeoPoint(48.24, -79.02), parseCoordinates(" 48.24 ", "-79.02"))
        assertEquals(GeoPoint(48.24, -79.02), parseCoordinates("48,24", "-79,02"))
        assertEquals(GeoPoint(-90.0, 180.0), parseCoordinates("-90", "180"))
    }

    @Test
    fun `anything else is no place`() {
        assertNull(parseCoordinates("", "-79"))
        assertNull(parseCoordinates("48", ""))
        assertNull(parseCoordinates("north", "-79"))
        assertNull(parseCoordinates("91", "0"))
        assertNull(parseCoordinates("0", "-181"))
    }
}
