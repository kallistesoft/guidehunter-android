package ca.kallistesoft.guidehunter.core.model

/**
 * A rectangle on the map, from its south-west to its north-east corner: the part of the map on screen. It can cross
 * the 180th meridian, where the west edge's longitude is greater than the east edge's.
 */
data class GeoBounds(val southWest: GeoPoint, val northEast: GeoPoint) {
    operator fun contains(point: GeoPoint): Boolean {
        if (point.latitude < southWest.latitude || point.latitude > northEast.latitude) return false
        val west = southWest.longitude
        val east = northEast.longitude
        return if (west <= east) point.longitude in west..east else point.longitude >= west || point.longitude <= east
    }
}
