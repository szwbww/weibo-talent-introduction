package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProviderResolverTest {
    private val resolver = ProviderResolver()

    @ParameterizedTest
    @CsvSource(
        "user@gmail.com, gmail",
        "user@googlemail.com, gmail",
        "user@outlook.com, outlook",
        "user@hotmail.com, outlook",
        "user@live.com, outlook",
        "user@msn.com, outlook",
        "user@yahoo.com, yahoo",
        "user@ymail.com, yahoo",
        "user@qq.com, tencent",
        "user@foxmail.com, tencent",
        "user@163.com, netease",
        "user@126.com, netease",
        "user@yeah.net, netease",
        "user@stanford.edu, edu",
        "user@mail.stanford.edu, edu",
        "user@cam.ac.uk, edu",
        "user@unknown-domain.com, other"
    )
    fun `resolve maps domain to provider`(email: String, expected: String) {
        assertEquals(expected, resolver.resolve(email))
    }

    @Test
    fun `resolve blank or null email returns other`() {
        assertEquals("other", resolver.resolve(null))
        assertEquals("other", resolver.resolve(""))
        assertEquals("other", resolver.resolve("not-an-email"))
    }
}
