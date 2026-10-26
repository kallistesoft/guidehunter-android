package ca.kallistesoft.guidehunter.core.data.di

import android.content.Context
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideApplicationRepository
import ca.kallistesoft.guidehunter.core.data.FirestoreGuideRepository
import ca.kallistesoft.guidehunter.core.data.FusedLocationRepository
import ca.kallistesoft.guidehunter.core.data.GuideApplicationRepository
import ca.kallistesoft.guidehunter.core.data.GuideRepository
import ca.kallistesoft.guidehunter.core.data.LocationRepository
import ca.kallistesoft.guidehunter.core.data.SampleGuideRepository
import ca.kallistesoft.guidehunter.core.data.UnconfiguredGuideApplicationRepository
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Where application emails go and whether Firebase is set up; the app provides it from its build. */
data class DataConfig(
    /** Who the "Become a guide" emails are sent to (must match firestore.rules). */
    val applicationsEmail: String,
)

/**
 * Firestore when the build has Firebase (`google-services.json`), otherwise sample guides and a form that can't be
 * sent: the app still builds and runs from a fresh clone.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    fun guideRepository(@ApplicationContext context: Context): GuideRepository =
        firestore(context)?.let(::FirestoreGuideRepository) ?: SampleGuideRepository()

    @Provides
    @Singleton
    fun guideApplicationRepository(@ApplicationContext context: Context, config: DataConfig): GuideApplicationRepository =
        firestore(context)?.let { FirestoreGuideApplicationRepository(it, config.applicationsEmail) }
            ?: UnconfiguredGuideApplicationRepository()

    @Provides
    fun locationRepository(@ApplicationContext context: Context): LocationRepository =
        FusedLocationRepository(context, LocationServices.getFusedLocationProviderClient(context))

    private fun firestore(context: Context): FirebaseFirestore? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()
}
