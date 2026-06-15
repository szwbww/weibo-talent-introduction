package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.config.EmailValidationProperties
import com.weibo.talentintroduction.expert.domain.EligibilityFilterSetting
import com.weibo.talentintroduction.expert.repository.EligibilityFilterSettingRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class EligibilityFilterServiceTest {

    private val candidateDefaults = CandidateFilterProperties(
        requireValidEmail = true, requireDoctoralDegree = false,
        excludeChineseNationality = true, enableAgeFilter = false, maxAgeExclusive = 70
    )
    private val academicDefaults = AcademicFilterProperties(
        enableHIndexFilter = false, minHIndex = 5,
        enableCitationFilter = false, minCitationCount = 50,
        enableActivityFilter = false, recentYearsThreshold = 5
    )
    private val emailDefaults = EmailValidationProperties(enableMxCheck = true)
    private val repository = mock(EligibilityFilterSettingRepository::class.java)

    private fun service(): EligibilityFilterService =
        EligibilityFilterService(repository, candidateDefaults, academicDefaults, emailDefaults)

    @Test
    fun `getAll returns defaults when DB empty`() {
        `when`(repository.findAll()).thenReturn(emptyList())
        val svc = service()
        val res = svc.getAll()
        assertEquals(true, res.candidateFilter.requireValidEmail)
        assertEquals(false, res.candidateFilter.requireDoctoralDegree)
        assertEquals(true, res.candidateFilter.excludeChineseNationality)
        assertEquals(70, res.candidateFilter.maxAgeExclusive)
        assertEquals(5, res.academicFilter.minHIndex)
        assertEquals(true, res.emailValidation.enableMxCheck)
    }

    @Test
    fun `getAll overrides from DB values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "candidate.requireValidEmail", settingValue = "false"),
            EligibilityFilterSetting(settingKey = "candidate.excludeChineseNationality", settingValue = "false"),
            EligibilityFilterSetting(settingKey = "academic.enableHIndexFilter", settingValue = "true"),
            EligibilityFilterSetting(settingKey = "academic.minHIndex", settingValue = "10"),
            EligibilityFilterSetting(settingKey = "email.enableMxCheck", settingValue = "false")
        ))
        val svc = service()
        val res = svc.getAll()
        assertEquals(false, res.candidateFilter.requireValidEmail)
        assertEquals(false, res.candidateFilter.excludeChineseNationality)
        assertEquals(true, res.academicFilter.enableHIndexFilter)
        assertEquals(10, res.academicFilter.minHIndex)
        assertEquals(false, res.emailValidation.enableMxCheck)
    }

    @Test
    fun `partial DB overrides still fallback to defaults`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "candidate.maxAgeExclusive", settingValue = "65")
        ))
        val svc = service()
        val res = svc.getAll()
        assertEquals(65, res.candidateFilter.maxAgeExclusive)
        assertEquals(true, res.candidateFilter.requireValidEmail)
        assertEquals(false, res.candidateFilter.requireDoctoralDegree)
        assertEquals(true, res.candidateFilter.excludeChineseNationality)
        assertEquals(false, res.candidateFilter.enableAgeFilter)
    }

    @Test
    fun `update persists and refreshes cache`() {
        `when`(repository.findAll()).thenReturn(emptyList())
        `when`(repository.findBySettingKey("candidate.requireValidEmail")).thenReturn(null)
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val svc = service()
        svc.update("candidate.requireValidEmail", "false")

        verify(repository).save(argThat { s ->
            (s as EligibilityFilterSetting).settingKey == "candidate.requireValidEmail" && s.settingValue == "false"
        })
    }

    @Test
    fun `getCandidateFilter returns typed values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "candidate.enableAgeFilter", settingValue = "true"),
            EligibilityFilterSetting(settingKey = "candidate.maxAgeExclusive", settingValue = "75")
        ))
        val svc = service()
        val cf = svc.getCandidateFilter()
        assertEquals(true, cf.enableAgeFilter)
        assertEquals(75, cf.maxAgeExclusive)
        assertEquals(true, cf.requireValidEmail)
    }

    @Test
    fun `getAcademicFilter returns typed values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "academic.enableCitationFilter", settingValue = "true"),
            EligibilityFilterSetting(settingKey = "academic.minCitationCount", settingValue = "100")
        ))
        val svc = service()
        val af = svc.getAcademicFilter()
        assertEquals(true, af.enableCitationFilter)
        assertEquals(100, af.minCitationCount)
        assertEquals(false, af.enableHIndexFilter)
    }

    @Test
    fun `getEmailValidationConfig returns overridden enableMxCheck`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "email.enableMxCheck", settingValue = "false")
        ))
        val svc = service()
        val ev = svc.getEmailValidationConfig()
        assertEquals(false, ev.enableMxCheck)
    }

    @Test
    fun `invalid int in DB falls back to default`() {
        `when`(repository.findAll()).thenReturn(listOf(
            EligibilityFilterSetting(settingKey = "candidate.maxAgeExclusive", settingValue = "not-a-number")
        ))
        val svc = service()
        val res = svc.getAll()
        assertEquals(70, res.candidateFilter.maxAgeExclusive)
    }
}
