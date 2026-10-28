package ca.kallistesoft.guidehunter.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import ca.kallistesoft.guidehunter.core.data.AuthRepository
import ca.kallistesoft.guidehunter.core.data.DataStoreSettingsRepository
import ca.kallistesoft.guidehunter.core.data.FirebaseAuthRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideRepository
import ca.kallistesoft.guidehunter.core.data.FusedLocationRepository
import ca.kallistesoft.guidehunter.core.data.GuideAccountRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SampleGuideRepository
import ca.kallistesoft.guidehunter.core.data.SettingsRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredAuthRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredGuideAccountRepository
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Where application emails go and whether Firebase is set up; the app provides it from its build. */
data class DataConfig(
    /** Who the new and edited offers are emailed to, for review (must match firestore.rules). */
    val applicationsEmail: String,
)

/**
 * Firebase when the build has it (`google-services.json`), otherwise sample guides and no guide sign-in: the app
 * still builds and runs from a fresh clone. Public so app tests can swap it for fakes (`@UninstallModules`).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun guideRepository(@ApplicationContext context: Context): GuideRepository =
        firestore(context)?.let(::FirestoreGuideRepository) ?: SampleGuideRepository()

    @Provides
    @Singleton
    fun authRepository(@ApplicationContext context: Context): AuthRepository =
        if (configured(context)) FirebaseAuthRepository(FirebaseAuth.getInstance()) else UnconfiguredAuthRepository()

    @Provides
    @Singleton
    fun guideAccountRepository(@ApplicationContext context: Context, config: DataConfig): GuideAccountRepository =
        firestore(context)?.let { FirestoreGuideAccountRepository(it, config.applicationsEmail) }
            ?: UnconfiguredGuideAccountRepository()

    @Provides
    fun locationRepository(@ApplicationContext context: Context): LocationRepository =
        FusedLocationRepository(context, LocationServices.getFusedLocationProviderClient(context))

    /** One DataStore per file: a singleton. */
    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })

    private fun configured(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    private fun firestore(context: Context): FirebaseFirestore? =
        if (configured(context)) FirebaseFirestore.getInstance() else null
}
