package ca.kallistesoft.guidehunter.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/**
 * The user's approximate location (coarse permission is enough to say "450 km away"), from Google Play services.
 * Nothing is read without the permission; the map asks for it.
 */
internal class FusedLocationRepository(
    private val context: Context,
    private val client: FusedLocationProviderClient,
) : LocationRepository {
    override suspend fun current(): GeoPoint? {
        if (!granted()) return null
        @Suppress("MissingPermission") // checked just above
        val location = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }.getOrNull()
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private fun granted(): Boolean = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
