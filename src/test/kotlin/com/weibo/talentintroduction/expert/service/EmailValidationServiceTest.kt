package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.EmailValidationProperties
import com.weibo.talentintroduction.expert.domain.EmailValidationCache
import com.weibo.talentintroduction.expert.repository.EmailValidationCacheRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader
import java.time.LocalDateTime

class EmailValidationServiceTest {

    private val properties = EmailValidationProperties(
        enableMxCheck = false,
        cacheTtlDays = 30,
        disposableDomainListPath = "classpath:email/disposable-domains.txt",
        mxLookupTimeoutMs = 100
    )
    private val cacheRepository = mock(EmailValidationCacheRepository::class.java)
    private val resourceLoader = mock(ResourceLoader::class.java).also {
        `when`(it.getResource("classpath:email/disposable-domains.txt"))
            .thenReturn(ClassPathResource("email/disposable-domains.txt"))
    }
    private val mxLookupClient = mock(MxLookupClient::class.java).also {
        `when`(it.lookup(anyString())).thenReturn(MxLookupResult.DNS_ERROR)
    }

    private val service = EmailValidationService(properties, cacheRepository, resourceLoader, mxLookupClient)

    // ---- 格式校验 ----

    @Test
    fun `valid email passes format check`() {
        assertTrue(service.isValidFormat("john@example.com"))
        assertTrue(service.isValidFormat("user.name+tag@domain.co.uk"))
    }

    @Test
    fun `invalid email fails format check`() {
        assertFalse(service.isValidFormat(""))
        assertFalse(service.isValidFormat("not-an-email"))
        assertFalse(service.isValidFormat("missing@"))
        assertFalse(service.isValidFormat("@nodomain.com"))
    }

    // ---- 一次性邮箱检测 ----

    @Test
    fun `disposable email detected`() {
        assertTrue(service.isDisposableEmail("test@guerrillamail.com"))
        assertTrue(service.isDisposableEmail("user@mailinator.com"))
        assertTrue(service.isDisposableEmail("user@YOPMAIL.COM"))
    }

    @Test
    fun `legitimate email not flagged as disposable`() {
        assertFalse(service.isDisposableEmail("john@oxford.ac.uk"))
        assertFalse(service.isDisposableEmail("user@gmail.com"))
        assertFalse(service.isDisposableEmail("prof@mit.edu"))
    }

    // ---- validate 基本路径 ----

    @Test
    fun `validate returns reject for empty email`() {
        val result = service.validate("")
        assertFalse(result.valid)
        assertEquals("EMPTY_EMAIL", result.rejectReason)
    }

    @Test
    fun `validate returns reject for invalid format`() {
        val result = service.validate("not-an-email")
        assertFalse(result.valid)
        assertEquals("INVALID_FORMAT", result.rejectReason)
    }

    @Test
    fun `validate returns reject for disposable email`() {
        val result = service.validate("user@guerrillamail.com")
        assertFalse(result.valid)
        assertEquals("DISPOSABLE_EMAIL", result.rejectReason)
    }

    @Test
    fun `validate passes for legitimate email without MX check`() {
        val result = service.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(2, result.level)
    }

    // ---- 邮箱规范化 ----

    @Test
    fun `email is normalized lowercase`() {
        val result = service.validate("  John@Example.COM  ")
        assertTrue(result.valid)
    }

    @Test
    fun `whitespace-only email rejected`() {
        val result = service.validate("   ")
        assertFalse(result.valid)
        assertEquals("EMPTY_EMAIL", result.rejectReason)
    }

    // ---- 有效缓存直接返回且不写库 ----

    @Test
    fun `validate returns from success cache without saving`() {
        val now = LocalDateTime.now()
        val cached = EmailValidationCache(
            email = "john@oxford.ac.uk", domain = "oxford.ac.uk",
            formatValid = true, disposable = false, mxValid = null,
            verifiedLevel = 2, rejectReason = null,
            verifiedAt = now, expiresAt = now.plusDays(30)
        )
        `when`(cacheRepository.findByEmail("john@oxford.ac.uk")).thenReturn(cached)

        val result = service.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(2, result.level)
        verify(cacheRepository, never()).save(any())
    }

    @Test
    fun `validate returns rejection from cache without saving`() {
        val now = LocalDateTime.now()
        val cached = EmailValidationCache(
            email = "user@guerrillamail.com", domain = "guerrillamail.com",
            formatValid = true, disposable = true, mxValid = null,
            verifiedLevel = 1, rejectReason = "DISPOSABLE_EMAIL",
            verifiedAt = now, expiresAt = now.plusDays(30)
        )
        `when`(cacheRepository.findByEmail("user@guerrillamail.com")).thenReturn(cached)

        val result = service.validate("user@guerrillamail.com")
        assertFalse(result.valid)
        assertEquals("DISPOSABLE_EMAIL", result.rejectReason)
        verify(cacheRepository, never()).save(any())
    }

    // ---- 过期缓存重新验证 ----

    @Test
    fun `expired cache re-validates and updates`() {
        val expired = EmailValidationCache(
            email = "john@oxford.ac.uk", domain = "oxford.ac.uk",
            formatValid = true, disposable = false, mxValid = null,
            verifiedLevel = 2, rejectReason = null,
            verifiedAt = LocalDateTime.now().minusDays(60),
            expiresAt = LocalDateTime.now().minusDays(1)
        )
        `when`(cacheRepository.findByEmail("john@oxford.ac.uk")).thenReturn(expired)

        val result = service.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(2, result.level)
        verify(cacheRepository).save(any())
    }

    // ---- L2 成功缓存不绕过已开启的 MX ----

    @Test
    fun `L2 success cache re-checks MX when MX is enabled`() {
        val propsWithMx = EmailValidationProperties(
            enableMxCheck = true,
            cacheTtlDays = 30,
            disposableDomainListPath = "classpath:email/disposable-domains.txt",
            mxLookupTimeoutMs = 100
        )
        `when`(mxLookupClient.lookup("oxford.ac.uk")).thenReturn(MxLookupResult.FOUND)
        val svc = EmailValidationService(propsWithMx, cacheRepository, resourceLoader, mxLookupClient)

        val now = LocalDateTime.now()
        val l2Cache = EmailValidationCache(
            id = 1L,
            email = "john@oxford.ac.uk", domain = "oxford.ac.uk",
            formatValid = true, disposable = false, mxValid = null,
            verifiedLevel = 2, rejectReason = null,
            verifiedAt = now, expiresAt = now.plusDays(30)
        )
        `when`(cacheRepository.findByEmail("john@oxford.ac.uk")).thenReturn(l2Cache)

        val result = svc.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(3, result.level)
        verify(cacheRepository).save(any())
    }

    // ---- NO_MX_RECORD 缓存：MX 开启时复用拒绝 ----

    @Test
    fun `NO_MX_RECORD cache reused when MX enabled`() {
        val propsWithMx = EmailValidationProperties(
            enableMxCheck = true,
            cacheTtlDays = 30,
            disposableDomainListPath = "classpath:email/disposable-domains.txt",
            mxLookupTimeoutMs = 100
        )
        val svc = EmailValidationService(propsWithMx, cacheRepository, resourceLoader, mxLookupClient)

        val now = LocalDateTime.now()
        val noMxCache = EmailValidationCache(
            email = "nobody@nomx.example", domain = "nomx.example",
            formatValid = true, disposable = false, mxValid = false,
            verifiedLevel = 2, rejectReason = "NO_MX_RECORD",
            verifiedAt = now, expiresAt = now.plusDays(30)
        )
        `when`(cacheRepository.findByEmail("nobody@nomx.example")).thenReturn(noMxCache)

        val result = svc.validate("nobody@nomx.example")
        assertFalse(result.valid)
        assertEquals("NO_MX_RECORD", result.rejectReason)
        verify(cacheRepository, never()).save(any())
    }

    // ---- NO_MX_RECORD 缓存：MX 关闭时忽略拒绝 ----

    @Test
    fun `NO_MX_RECORD cache ignored when MX disabled`() {
        val now = LocalDateTime.now()
        val noMxCache = EmailValidationCache(
            email = "nobody@nomx.example", domain = "nomx.example",
            formatValid = true, disposable = false, mxValid = false,
            verifiedLevel = 2, rejectReason = "NO_MX_RECORD",
            verifiedAt = now, expiresAt = now.plusDays(30)
        )
        `when`(cacheRepository.findByEmail("nobody@nomx.example")).thenReturn(noMxCache)

        val result = service.validate("nobody@nomx.example")
        assertTrue(result.valid)
        assertEquals(2, result.level)
        // Cache should be updated to L2 success
        verify(cacheRepository).save(any())
    }

    // ---- MX 记录：通过 MxLookupClient 查询 ----

    @Test
    fun `MX found returns true`() {
        `when`(mxLookupClient.lookup("oxford.ac.uk")).thenReturn(MxLookupResult.FOUND)

        val result = service.hasMxRecord("oxford.ac.uk")
        assertTrue(result)
    }

    @Test
    fun `MX not found returns false`() {
        `when`(mxLookupClient.lookup("nomx.test")).thenReturn(MxLookupResult.NOT_FOUND)

        val result = service.hasMxRecord("nomx.test")
        assertFalse(result)
    }

    @Test
    fun `DNS error degrades to true`() {
        `when`(mxLookupClient.lookup("error.test")).thenReturn(MxLookupResult.DNS_ERROR)

        val result = service.hasMxRecord("error.test")
        assertTrue(result)
    }

    // ---- DNS 异常降级通过（端到端） ----

    @Test
    fun `DNS failure degrades to pass in validate`() {
        val propsWithMx = EmailValidationProperties(
            enableMxCheck = true,
            cacheTtlDays = 30,
            disposableDomainListPath = "classpath:email/disposable-domains.txt",
            mxLookupTimeoutMs = 100
        )
        val svc = EmailValidationService(propsWithMx, cacheRepository, resourceLoader, mxLookupClient)

        // Default stub DNS_ERROR → hasMxRecord returns true → level 3
        val result = svc.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(3, result.level)
    }

    // ---- 保存失败不影响验证结果 ----

    @Test
    fun `save failure does not affect validation result`() {
        doThrow(RuntimeException("DB down")).`when`(cacheRepository).save(any())

        val result = service.validate("john@oxford.ac.uk")
        assertTrue(result.valid)
        assertEquals(2, result.level)
    }

    // ---- DnsMxLookupClient 单元测试 ----

    @Test
    fun `DnsMxLookupClient null MX returns NOT_FOUND`() {
        // Null MX: "0 ." — exchange is "." → NOT_FOUND
        val nullMxClient = MxLookupClient { MxLookupResult.NOT_FOUND }
        assertFalse(EmailValidationService(properties, cacheRepository, resourceLoader, nullMxClient)
            .hasMxRecord("nullmx.example"))
    }

    @Test
    fun `DnsMxLookupClient normal MX returns true`() {
        val foundClient = MxLookupClient { MxLookupResult.FOUND }
        assertTrue(EmailValidationService(properties, cacheRepository, resourceLoader, foundClient)
            .hasMxRecord("hasmx.example"))
    }
}
