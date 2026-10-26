package ca.kallistesoft.guidehunter.core.data.di

import android.app.Application
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideApplicationRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideRepository
import ca.kallistesoft.guidehunter.core.data.FusedLocationRepository
import ca.kallistesoft.guidehunter.core.data.SampleGuideRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredGuideApplicationRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Firestore when the build has Firebase, samples otherwise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataModuleTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val config = DataConfig("owner@example.com")

    @After
    fun tearDown() {
        FirebaseApp.getApps(app).forEach { it.delete() }
        unmockkAll()
    }

    @Test
    fun `without Firebase the app shows sample guides and can't send forms`() {
        assertTrue(DataModule.guideRepository(app) is SampleGuideRepository)
        assertTrue(DataModule.guideApplicationRepository(app, config) is UnconfiguredGuideApplicationRepository)
        assertTrue(DataModule.locationRepository(app) is FusedLocationRepository)
    }

    @Test
    fun `with Firebase the guides and forms go through Firestore`() {
        val options = FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("key").setProjectId("test").build()
        FirebaseApp.initializeApp(app, options)
        // Firestore registers itself through the app manifest, which a library's unit tests don't merge.
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockk()
        assertTrue(DataModule.guideRepository(app) is FirestoreGuideRepository)
        assertTrue(DataModule.guideApplicationRepository(app, config) is FirestoreGuideApplicationRepository)
    }
}
