package ca.kallistesoft.guidehunter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestsTest {
    @Test
    fun `a contact needs a name, and a phone of 7 digits when there is one`() {
        assertEquals(setOf(ContactField.NAME), UserContact(" ", "a@b.ca").errors())
        assertTrue(UserContact("Alex", "a@b.ca").isComplete)
        assertEquals(setOf(ContactField.PHONE), UserContact("Alex", "a@b.ca", "555 01").errors())
        assertTrue(UserContact("Alex", "a@b.ca", "+1 418 555 0199").isComplete)
        assertFalse(UserContact().isComplete)
    }

    @Test
    fun `a contact is saved trimmed`() {
        assertEquals(UserContact("Alex", "a@b.ca", "418"), UserContact(" Alex ", " a@b.ca", "418 ").trimmed())
    }

    @Test
    fun `pending and accepted requests are open, the others closed`() {
        assertEquals(
            listOf(RequestStatus.PENDING, RequestStatus.APPROVED),
            RequestStatus.entries.filter { it.open },
        )
    }

    @Test
    fun `one request per offer and user`() {
        assertEquals("offer1_user1", InterestRequest.requestId("offer1", "user1"))
    }
}
