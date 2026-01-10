package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a notification schedule rule.
 *
 * Supports multiple rule types:
 * - INTERVAL: Every X units (minutes/hours/days/weeks)
 * - DAILY: At specific time(s) each day
 * - WEEKLY: On specific day(s) at specific time(s)
 * - ONETIME: At a specific date and time
 */
@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val userId: String,

    /**
     * Rule type: INTERVAL, DAILY, WEEKLY, ONETIME
     */
    val type: String,

    /**
     * For INTERVAL: the interval value (e.g., 6 for "every 6 hours")
     * For DAILY: not used (0)
     * For WEEKLY: bitmask of days (1=Mon, 2=Tue, 4=Wed, 8=Thu, 16=Fri, 32=Sat, 64=Sun)
     * For ONETIME: not used (0)
     */
    val intervalValue: Int = 0,

    /**
     * For INTERVAL: the unit (MINUTES, HOURS, DAYS, WEEKS)
     * For others: not used (empty)
     */
    val intervalUnit: String = "",

    /**
     * Hour of day (0-23) for DAILY, WEEKLY, ONETIME rules
     */
    val hour: Int = 0,

    /**
     * Minute (0-59) for DAILY, WEEKLY, ONETIME rules
     */
    val minute: Int = 0,

    /**
     * For ONETIME: target date in ISO format (yyyy-MM-dd)
     * For others: empty string
     */
    val targetDate: String = "",

    /**
     * Whether this rule is currently enabled
     */
    val enabled: Boolean = true,

    /**
     * Creation timestamp
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Last modified timestamp
     */
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Schedule rule types
 */
object ScheduleRuleType {
    const val INTERVAL = "INTERVAL"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val ONETIME = "ONETIME"
}

/**
 * Interval units for INTERVAL type rules
 */
object IntervalUnit {
    const val MINUTES = "MINUTES"
    const val HOURS = "HOURS"
    const val DAYS = "DAYS"
    const val WEEKS = "WEEKS"
}

/**
 * Day of week bitmask values
 */
object DayOfWeek {
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 4
    const val THURSDAY = 8
    const val FRIDAY = 16
    const val SATURDAY = 32
    const val SUNDAY = 64

    fun isDaySelected(bitmask: Int, day: Int): Boolean = (bitmask and day) != 0

    fun toggleDay(bitmask: Int, day: Int): Int = bitmask xor day

    fun allDays(): Int = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY

    fun weekdays(): Int = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY

    fun weekends(): Int = SATURDAY or SUNDAY
}
