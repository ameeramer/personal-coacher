package com.personalcoacher.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Utilities for handling journal entry dates with custom day boundary logic.
 *
 * The "logical day" considers entries written between midnight and 6:00 AM as
 * belonging to the previous day. This is useful for late-night journal entries
 * where the user is writing about "today" but it's technically already past midnight.
 */
object DateUtils {

    /**
     * The hour at which a new "logical day" begins.
     * Entries created before this hour are considered part of the previous day.
     */
    const val DAY_BOUNDARY_HOUR = 6

    /**
     * Gets the logical date for a journal entry based on the current time.
     * If the current time is between 00:00 and 06:00, returns yesterday's date.
     * Otherwise, returns today's date.
     *
     * @param zoneId The timezone to use for date calculations. Defaults to system default.
     * @return The logical date as an Instant (start of the logical day in the given timezone)
     */
    fun getLogicalDate(zoneId: ZoneId = ZoneId.systemDefault()): Instant {
        val now = ZonedDateTime.now(zoneId)
        val logicalDate = if (now.hour < DAY_BOUNDARY_HOUR) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
        // Return the start of the logical day
        return logicalDate.atStartOfDay(zoneId).toInstant()
    }

    /**
     * Gets the logical LocalDate for a journal entry based on the current time.
     * If the current time is between 00:00 and 06:00, returns yesterday's date.
     * Otherwise, returns today's date.
     *
     * @param zoneId The timezone to use for date calculations. Defaults to system default.
     * @return The logical date as a LocalDate
     */
    fun getLogicalLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        val now = ZonedDateTime.now(zoneId)
        return if (now.hour < DAY_BOUNDARY_HOUR) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }

    /**
     * Converts a LocalDate to an Instant representing the start of that day.
     *
     * @param localDate The date to convert
     * @param zoneId The timezone to use. Defaults to system default.
     * @return An Instant at the start of the given day in the given timezone
     */
    fun localDateToInstant(localDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Instant {
        return localDate.atStartOfDay(zoneId).toInstant()
    }

    /**
     * Converts an Instant to a LocalDate.
     *
     * @param instant The instant to convert
     * @param zoneId The timezone to use. Defaults to system default.
     * @return The LocalDate in the given timezone
     */
    fun instantToLocalDate(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return instant.atZone(zoneId).toLocalDate()
    }
}
