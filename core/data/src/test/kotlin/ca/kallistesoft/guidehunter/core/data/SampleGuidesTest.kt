package ca.kallistesoft.guidehunter.core.data

import ca.kallistesoft.guidehunter.core.testing.TestGuides
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleGuidesTest {
    @Test
    fun `every sample guide has a contact`() = runTest {
        val repository = SampleGuideRepository()
        val guides = repository.guides.first()
        assertEquals(SAMPLE_GUIDES, guides)
        assertTrue(guides.all { repository.contact(it.id) != null })
        assertNull(repository.contact("unknown"))
    }

    @Test(expected = BackendNotConfiguredException::class)
    fun `without Firebase a form can't be sent`() = runTest {
        UnconfiguredGuideApplicationRepository().submit(TestGuides.application)
    }
}
