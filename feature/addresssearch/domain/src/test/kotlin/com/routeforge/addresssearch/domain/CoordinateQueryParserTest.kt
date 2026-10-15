package com.routeforge.addresssearch.domain

import com.routeforge.coredomain.GeoMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoordinateQueryParserTest {
    @Test
    fun `Google decimal example preserves latitude longitude and precision`() {
        assertEquals(
            CoordinateQuery.Position(35.68309115595934, 139.75222770699756),
            CoordinateQueryParser.parse("35.68309115595934, 139.75222770699756"),
        )
    }

    @Test
    fun `parenthesized integer example is longitude latitude scaled by one hundred thousand`() {
        assertEquals(CoordinateQuery.Position(35.68309, 139.75223), CoordinateQueryParser.parse("(13975223, 3568309)"))
    }

    @Test
    fun `the two supplied Tokyo examples represent positions within one metre`() {
        val first = CoordinateQueryParser.parse("35.68309115595934, 139.75222770699756") as CoordinateQuery.Position
        val second = CoordinateQueryParser.parse("(13975223, 3568309)") as CoordinateQuery.Position
        assertTrue(GeoMath.haversineMeters(first.latitude, first.longitude, second.latitude, second.longitude) < 1.0)
    }

    @Test
    fun `negative coordinates and signs work in both formats`() {
        val expected = CoordinateQuery.Position(40.7128, -74.006)
        assertEquals(expected, CoordinateQueryParser.parse("+40.7128, -74.006"))
        assertEquals(expected, CoordinateQueryParser.parse("(-7400600, +4071280)"))
    }

    @Test
    fun `full width punctuation and surrounding whitespace are accepted`() {
        assertEquals(CoordinateQuery.Position(35.68309, 139.75223), CoordinateQueryParser.parse("  （13975223， 3568309） \n"))
        assertEquals(CoordinateQuery.Position(35.68, 139.75), CoordinateQueryParser.parse(" 35.68， 139.75 "))
    }

    @Test
    fun `plain integer pairs remain latitude longitude`() {
        assertEquals(CoordinateQuery.Position(35.0, 139.0), CoordinateQueryParser.parse("35, 139"))
    }

    @Test
    fun `parentheses around decimal degrees keep latitude longitude order`() {
        assertEquals(CoordinateQuery.Position(35.68, 139.75), CoordinateQueryParser.parse("(35.68, 139.75)"))
    }

    @Test
    fun `zero and geographic boundaries are valid`() {
        assertEquals(CoordinateQuery.Position(0.0, 0.0), CoordinateQueryParser.parse("0, 0"))
        assertEquals(CoordinateQuery.Position(-90.0, 180.0), CoordinateQueryParser.parse("-90,180"))
        assertEquals(CoordinateQuery.Position(90.0, -180.0), CoordinateQueryParser.parse("(-18000000,9000000)"))
    }

    @Test
    fun `invalid numbers shapes overflow and coordinate order are never silently corrected`() {
        for (query in listOf(
            "91, 139", "35, 181", "139.75223, 35.68309", "(3568309,13975223)",
            "35.6,139.7,40", "35 68,139", "(13975223,3568309", "13975223,3568309)",
            "(999999999999999999999999,3568309)", "NaN,139", "35,Infinity", "1e999,139",
        )) {
            assertEquals(CoordinateQuery.InvalidCoordinates, CoordinateQueryParser.parse(query), query)
        }
    }

    @Test
    fun `place names and comma separated street addresses are not coordinate queries`() {
        for (query in listOf("Tokyo Imperial Palace", "Rue Example, 123", "123 Main Street, Paris", "Beijing", "123")) {
            assertEquals(CoordinateQuery.NotCoordinates, CoordinateQueryParser.parse(query), query)
        }
    }
}
