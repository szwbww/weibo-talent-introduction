package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpertIdNormalizerTest {
    @Test
    fun `normalizes generated email id without uppercasing hash`() {
        assertEquals(
            "EMAIL-f07688e64d3dc212a4d",
            ExpertIdNormalizer.normalize(" email-F07688E64D3DC212A4D ")
        )
    }

    @Test
    fun `normalizes ORCID ids to uppercase for existing dedupe behavior`() {
        assertEquals("0000-0001-ABCD-EFGH", ExpertIdNormalizer.normalize(" 0000-0001-abcd-efgh "))
    }
}
