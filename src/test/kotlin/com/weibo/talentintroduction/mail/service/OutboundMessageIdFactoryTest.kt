package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutboundMessageIdFactoryTest {

    @Test
    fun `newId produces expected shape`() {
        val id = OutboundMessageIdFactory.newId("meeting-invitation", "42", "lilei@talents.szwebotech.cn")
        assertTrue(
            id.matches(Regex("^<meeting-invitation-42-[0-9a-f-]{36}@talents\\.szwebotech\\.cn>$")),
            "unexpected messageId: $id"
        )
    }

    @Test
    fun `newId is unique across 1000 calls`() {
        val ids = (1..1000).map {
            OutboundMessageIdFactory.newId("auto-reply", "11", "sender@qftechtalent.com")
        }
        assertEquals(1000, ids.distinct().size)
    }

    @Test
    fun `newId rejects senderEmail without at sign`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboundMessageIdFactory.newId("auto-reply", "11", "no-at-sign")
        }
    }

    @Test
    fun `newId rejects senderEmail with empty domain`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboundMessageIdFactory.newId("auto-reply", "11", "user@")
        }
    }

    @Test
    fun `newId rejects blank kind`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboundMessageIdFactory.newId(" ", "11", "sender@qftechtalent.com")
        }
    }

    @Test
    fun `newId rejects blank discriminator`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboundMessageIdFactory.newId("auto-reply", " ", "sender@qftechtalent.com")
        }
    }

    @Test
    fun `newId output fits mail_record message_id column`() {
        val id = OutboundMessageIdFactory.newId(
            "meeting-invitation",
            "0000-0001-2345-6789",
            "lilei@talents.szwebotech.cn"
        )
        assertTrue(id.length <= 255, "messageId length ${id.length} exceeds 255")
    }
}
