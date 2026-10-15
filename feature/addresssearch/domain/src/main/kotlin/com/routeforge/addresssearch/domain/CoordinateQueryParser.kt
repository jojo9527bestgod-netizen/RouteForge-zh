package com.routeforge.addresssearch.domain

sealed interface CoordinateQuery {
    data object NotCoordinates : CoordinateQuery
    data object InvalidCoordinates : CoordinateQuery
    data class Position(val latitude: Double, val longitude: Double) : CoordinateQuery
}

/** Plain pairs are latitude/longitude; parenthesized integer pairs are longitude/latitude × 100000. */
object CoordinateQueryParser {
    private val number = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val integer = Regex("[+-]?[0-9]+")

    fun parse(query: String): CoordinateQuery {
        val text = query.trim().removeSurrounding("\"").removeSurrounding("“", "”")
            .replace('，', ',').replace('（', '(').replace('）', ')').replace('−', '-')
        if (',' !in text) return CoordinateQuery.NotCoordinates
        val parenthesized = text.startsWith('(') && text.endsWith(')')
        val body = if (parenthesized) text.substring(1, text.lastIndex).trim() else text
        val parts = body.split(',').map(String::trim)
        val numericOnly = text.any(Char::isDigit) && text.all { it.isDigit() || it.isWhitespace() || it in "+-.,()" }
        val numericTokens = parts.size == 2 && parts.all {
            number.matches(it) || it.equals("NaN", ignoreCase = true) ||
                it.removePrefix("+").removePrefix("-").equals("Infinity", ignoreCase = true)
        }
        // Addresses such as "Rue Example, 123" must still go to normal place search.
        if (!numericOnly && !numericTokens) return CoordinateQuery.NotCoordinates
        if (text.length > 256 || parts.size != 2 || parts.any { !number.matches(it) }) return CoordinateQuery.InvalidCoordinates

        val latitude: Double
        val longitude: Double
        if (parenthesized && parts.all(integer::matches)) {
            val longitudeInteger = parts[0].toLongOrNull() ?: return CoordinateQuery.InvalidCoordinates
            val latitudeInteger = parts[1].toLongOrNull() ?: return CoordinateQuery.InvalidCoordinates
            longitude = longitudeInteger / 100_000.0
            latitude = latitudeInteger / 100_000.0
        } else {
            latitude = parts[0].toDoubleOrNull() ?: return CoordinateQuery.InvalidCoordinates
            longitude = parts[1].toDoubleOrNull() ?: return CoordinateQuery.InvalidCoordinates
        }
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) {
            return CoordinateQuery.InvalidCoordinates
        }
        return CoordinateQuery.Position(latitude, longitude)
    }
}
