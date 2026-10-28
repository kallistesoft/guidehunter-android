package ca.kallistesoft.guidehunter.core.data.di

import ca.kallistesoft.guidehunter.core.model.Today
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** The device's date, apart from [DataModule] so tests that swap the data for fakes keep it. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun today(): Today = Today { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
}
