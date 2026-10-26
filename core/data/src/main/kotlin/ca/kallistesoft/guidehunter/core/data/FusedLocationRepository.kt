package ca.kallistesoft.guidehunter.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
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
        val location = try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // location off, Play services missing: no distance, nothing else
        }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /** Coarse is all the app asks for: a few km of error don't change "about 450 km away". */
    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
