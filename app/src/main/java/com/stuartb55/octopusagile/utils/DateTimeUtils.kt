package com.stuartb55.octopusagile.utils

import android.util.Log
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private const val TAG = "DateTimeUtils"

/**
 * Formats a UTC date-time string to show only the time in the target timezone.
 * Example: "10:00:00Z" (UTC) for "Europe/London" during BST becomes "11:00".
 *
 * @param dateTimeStringUtc The UTC date-time string (e.g., "2023-12-15T10:00:00Z").
 * @param targetZoneId The timezone for display (defaults to "Europe/London").
 * @return A formatted time string (e.g., "11:00") or a fallback string on error.
 */
fun formatTimeForDisplay(
    dateTimeStringUtc: String,
    targetZoneId: ZoneId = ZoneId.of("Europe/London")
): String {
    return try {
        if (dateTimeStringUtc.isBlank()) return "N/A"
        val utcDateTime = OffsetDateTime.parse(dateTimeStringUtc)
        val targetLocalDateTime = utcDateTime.atZoneSameInstant(targetZoneId).toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        targetLocalDateTime.format(formatter)
    } catch (e: DateTimeParseException) {
        Log.e(TAG, "Failed to parse time string: $dateTimeStringUtc", e)
        // Fallback: Try to extract time part from original string or show placeholder
        dateTimeStringUtc.substringAfter("T", "").substringBeforeLast(":", "").take(5)
            .ifEmpty { "N/A" }
    } catch (e: Exception) {
        Log.e(TAG, "Error formatting time string: $dateTimeStringUtc", e)
        "N/A" // Generic placeholder for other errors
    }
}

/**
 * Formats a UTC date-time string to show both date and time in the target timezone.
 * Example: "2023-12-15T10:00:00Z" (UTC) for "Europe/London" during BST becomes "15/12 11:00".
 *
 * @param dateTimeStringUtc The UTC date-time string (e.g., "2023-12-15T10:00:00Z").
 * @param targetZoneId The timezone for display (defaults to "Europe/London").
 * @return A formatted date and time string (e.g., "15/12 11:00") or a fallback string on error.
 */
fun formatDateAndTimeForDisplay(
    dateTimeStringUtc: String,
    targetZoneId: ZoneId = ZoneId.of("Europe/London")
): String {
    return try {
        if (dateTimeStringUtc.isBlank()) return "Invalid Date"
        val utcDateTime = OffsetDateTime.parse(dateTimeStringUtc)
        val targetZonedDateTime = utcDateTime.atZoneSameInstant(targetZoneId)
        val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm") // Format like "28/05 11:00"
        targetZonedDateTime.format(formatter)
    } catch (e: DateTimeParseException) {
        Log.e(TAG, "Failed to parse date-time string: $dateTimeStringUtc", e)
        // Fallback: Return a simplified version of original or an error message
        val simplified = dateTimeStringUtc.replace("T", " ").substringBefore("Z")
        if (simplified.length > 5) simplified else "Invalid Date"
    } catch (e: Exception) {
        Log.e(TAG, "Error formatting date-time string: $dateTimeStringUtc", e)
        "Invalid Date"
    }
}