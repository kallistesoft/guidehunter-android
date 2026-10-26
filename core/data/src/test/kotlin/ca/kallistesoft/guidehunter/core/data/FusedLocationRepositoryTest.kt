package ca.kallistesoft.guidehunter.core.data

import android.Manifest
import android.app.Application
import android.location.Location
import ca.kallistesoft.guidehunter.core.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FusedLocationRepositoryTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val client = mockk<FusedLocationProviderClient>()
    private val repository = FusedLocationRepository(app, client)

    @Test
    fun `without the permission nothing is read`() = runTest {
        assertNull(repository.current())
        verify(exactly = 0) { client.getCurrentLocation(any<Int>(), any()) }
    }

    @Test
    fun `with the permission the current location is read, or none`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val location = Location("test").apply {
            latitude = 46.8
            longitude = -71.2
        }
        every { client.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(location)
        assertEquals(GeoPoint(46.8, -71.2), repository.current())

        every { client.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(null)
        assertNull("no fix", repository.current())

        every { client.getCurrentLocation(any<Int>(), any()) } returns Tasks.forException(IllegalStateException())
        assertNull("location off", repository.current())
    }
}
