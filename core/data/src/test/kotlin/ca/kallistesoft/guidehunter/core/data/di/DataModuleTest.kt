package ca.kallistesoft.guidehunter.core.data.di

import android.app.Application
import ca.kallistesoft.guidehunter.core.data.DataStoreSettingsRepository
import ca.kallistesoft.guidehunter.core.data.FirebaseAuthRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideRepository
import ca.kallistesoft.guidehunter.core.data.FusedLocationRepository
import ca.kallistesoft.guidehunter.core.data.SampleGuideRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredAuthRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredGuideAccountRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val now = TimeModule.now()

    @After
    fun tearDown() {
        FirebaseApp.getApps(app).forEach { it.delete() }
        unmockkAll()
    }

    @Test
    fun `without Firebase the app shows sample guides and guides can't sign in`() {
        assertTrue(DataModule.guideRepository(app) is SampleGuideRepository)
        assertTrue(DataModule.authRepository(app) is UnconfiguredAuthRepository)
        assertTrue(DataModule.guideAccountRepository(app, config, now) is UnconfiguredGuideAccountRepository)
        assertTrue(DataModule.locationRepository(app) is FusedLocationRepository)
        assertTrue(DataModule.settingsRepository(app) is DataStoreSettingsRepository)
    }

    @Test
    fun `the time is the device's`() {
        val before = Clock.System.now()
        val now = TimeModule.now()()
        assertTrue(now >= before && now <= Clock.System.now())
        assertEquals(Clock.System.todayIn(TimeZone.currentSystemDefault()), TimeModule.today()())
    }

    @Test
    fun `with Firebase the guides and accounts go through Firestore and Firebase Auth`() {
        val options = FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("key").setProjectId("test").build()
        FirebaseApp.initializeApp(app, options)
        // Firestore registers itself through the app manifest, which a library's unit tests don't merge.
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockk()
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockk()
        assertTrue(DataModule.guideRepository(app) is FirestoreGuideRepository)
        assertTrue(DataModule.authRepository(app) is FirebaseAuthRepository)
        assertTrue(DataModule.guideAccountRepository(app, config, now) is FirestoreGuideAccountRepository)
    }
}
