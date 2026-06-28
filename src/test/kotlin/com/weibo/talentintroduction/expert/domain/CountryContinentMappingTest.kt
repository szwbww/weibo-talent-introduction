package com.weibo.talentintroduction.expert.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CountryContinentMappingTest {

    @Test
    fun `maps china variants to China region`() {
        assertEquals(CountryContinentMapping.REGION_CHINA, CountryContinentMapping.toRegion("China"))
        assertEquals(CountryContinentMapping.REGION_CHINA, CountryContinentMapping.toRegion("CN"))
        assertEquals(CountryContinentMapping.REGION_CHINA, CountryContinentMapping.toRegion("Chinese"))
    }

    @Test
    fun `maps japan and korea variants to Asia Japan and Korea region`() {
        assertEquals(CountryContinentMapping.REGION_ASIA_JK, CountryContinentMapping.toRegion("Japan"))
        assertEquals(CountryContinentMapping.REGION_ASIA_JK, CountryContinentMapping.toRegion("JP"))
        assertEquals(CountryContinentMapping.REGION_ASIA_JK, CountryContinentMapping.toRegion("Korea"))
        assertEquals(CountryContinentMapping.REGION_ASIA_JK, CountryContinentMapping.toRegion("KR"))
        assertEquals(CountryContinentMapping.REGION_ASIA_JK, CountryContinentMapping.toRegion("South Korea"))
    }

    @Test
    fun `maps other asia variants to Asia Other region`() {
        assertEquals(CountryContinentMapping.REGION_ASIA_OTHER, CountryContinentMapping.toRegion("India"))
        assertEquals(CountryContinentMapping.REGION_ASIA_OTHER, CountryContinentMapping.toRegion("IN"))
        assertEquals(CountryContinentMapping.REGION_ASIA_OTHER, CountryContinentMapping.toRegion("Singapore"))
        assertEquals(CountryContinentMapping.REGION_ASIA_OTHER, CountryContinentMapping.toRegion("SG"))
    }

    @Test
    fun `maps north america variants`() {
        assertEquals(CountryContinentMapping.REGION_NORTH_AMERICA, CountryContinentMapping.toRegion("US"))
        assertEquals(CountryContinentMapping.REGION_NORTH_AMERICA, CountryContinentMapping.toRegion("United States"))
        assertEquals(CountryContinentMapping.REGION_NORTH_AMERICA, CountryContinentMapping.toRegion("Canada"))
    }

    @Test
    fun `maps south america variants`() {
        assertEquals(CountryContinentMapping.REGION_SOUTH_AMERICA, CountryContinentMapping.toRegion("Brazil"))
        assertEquals(CountryContinentMapping.REGION_SOUTH_AMERICA, CountryContinentMapping.toRegion("Argentina"))
    }

    @Test
    fun `maps europe variants`() {
        assertEquals(CountryContinentMapping.REGION_EUROPE, CountryContinentMapping.toRegion("GB"))
        assertEquals(CountryContinentMapping.REGION_EUROPE, CountryContinentMapping.toRegion("Germany"))
    }

    @Test
    fun `maps null blank and unknown to Other`() {
        assertEquals(CountryContinentMapping.REGION_OTHER, CountryContinentMapping.toRegion(null))
        assertEquals(CountryContinentMapping.REGION_OTHER, CountryContinentMapping.toRegion(""))
        assertEquals(CountryContinentMapping.REGION_OTHER, CountryContinentMapping.toRegion("   "))
        assertEquals(CountryContinentMapping.REGION_OTHER, CountryContinentMapping.toRegion("xyzabc"))
    }

    @Test
    fun `countriesForRegion China includes china and cn`() {
        val countries = CountryContinentMapping.countriesForRegion(CountryContinentMapping.REGION_CHINA)
        assertTrue(countries.contains("china"))
        assertTrue(countries.contains("cn"))
        assertTrue(countries.contains("CN"))
    }

    @Test
    fun `countriesForRegion Asia Japan and Korea includes japan and korea`() {
        val countries = CountryContinentMapping.countriesForRegion(CountryContinentMapping.REGION_ASIA_JK)
        assertTrue(countries.contains("japan"))
        assertTrue(countries.contains("korea"))
        assertTrue(countries.contains("JP"))
    }
}
