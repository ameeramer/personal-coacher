package com.personalcoacher.domain.model

import com.personalcoacher.data.local.entity.DayOfWeek
import com.personalcoacher.data.local.entity.IntervalUnit
import com.personalcoacher.data.local.entity.ScheduleRuleEntity
import com.personalcoacher.data.local.entity.ScheduleRuleType
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Domain model for a notification schedule rule
 */
data class ScheduleRule(
    val id: String,
    val userId: String,
    val type: RuleType,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns a human-readable description of this schedule rule
     */
    fun getDescription(): String {
        return when (type) {
            is RuleType.Interval -> {
                val unitStr = when (type.unit) {
                    IntervalUnit.MINUTES -> if (type.value == 1) "minute" else "minutes"
                    IntervalUnit.HOURS -> if (type.value == 1) "hour" else "hours"
                    IntervalUnit.DAYS -> if (type.value == 1) "day" else "days"
                    IntervalUnit.WEEKS -> if (type.value == 1) "week" else "weeks"
                    else -> type.unit.lowercase()
                }
                "Every ${type.value} $unitStr"
            }
            is RuleType.Daily -> {
                val timeStr = formatTime(type.hour, type.minute)
                "Daily at $timeStr"
            }
            is RuleType.Weekly -> {
                val timeStr = formatTime(type.hour, type.minute)
                val daysStr = formatDays(type.daysBitmask)
                "$daysStr at $timeStr"
            }
            is RuleType.OneTime -> {
                val timeStr = formatTime(type.hour, type.minute)
                val dateStr = formatDate(type.date)
                "$dateStr at $timeStr"
            }
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun formatDays(bitmask: Int): String {
        val days = mutableListOf<String>()
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.MONDAY)) days.add("Mon")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.TUESDAY)) days.add("Tue")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.WEDNESDAY)) days.add("Wed")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.THURSDAY)) days.add("Thu")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.FRIDAY)) days.add("Fri")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.SATURDAY)) days.add("Sat")
        if (DayOfWeek.isDaySelected(bitmask, DayOfWeek.SUNDAY)) days.add("Sun")

        return when {
            bitmask == DayOfWeek.allDays() -> "Every day"
            bitmask == DayOfWeek.weekdays() -> "Weekdays"
            bitmask == DayOfWeek.weekends() -> "Weekends"
            days.size == 1 -> "Every ${days[0]}"
            else -> days.joinToString(", ")
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
            date.format(formatter)
        } catch (e: Exception) {
            dateStr
        }
    }
}

/**
 * Sealed class representing different schedule rule types
 */
sealed class RuleType {
    data class Interval(val value: Int, val unit: String) : RuleType()
    data class Daily(val hour: Int, val minute: Int) : RuleType()
    data class Weekly(val daysBitmask: Int, val hour: Int, val minute: Int) : RuleType()
    data class OneTime(val date: String, val hour: Int, val minute: Int) : RuleType()
}

/**
 * Extension function to convert ScheduleRuleEntity to domain model
 */
fun ScheduleRuleEntity.toDomainModel(): ScheduleRule {
    val ruleType = when (type) {
        ScheduleRuleType.INTERVAL -> RuleType.Interval(intervalValue, intervalUnit)
        ScheduleRuleType.DAILY -> RuleType.Daily(hour, minute)
        ScheduleRuleType.WEEKLY -> RuleType.Weekly(intervalValue, hour, minute)
        ScheduleRuleType.ONETIME -> RuleType.OneTime(targetDate, hour, minute)
        else -> RuleType.Interval(6, IntervalUnit.HOURS) // Default fallback
    }
    return ScheduleRule(
        id = id,
        userId = userId,
        type = ruleType,
        enabled = enabled,
        createdAt = createdAt
    )
}

/**
 * Extension function to convert domain model to entity
 */
fun ScheduleRule.toEntity(): ScheduleRuleEntity {
    return when (type) {
        is RuleType.Interval -> ScheduleRuleEntity(
            id = id,
            userId = userId,
            type = ScheduleRuleType.INTERVAL,
            intervalValue = type.value,
            intervalUnit = type.unit,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        is RuleType.Daily -> ScheduleRuleEntity(
            id = id,
            userId = userId,
            type = ScheduleRuleType.DAILY,
            hour = type.hour,
            minute = type.minute,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        is RuleType.Weekly -> ScheduleRuleEntity(
            id = id,
            userId = userId,
            type = ScheduleRuleType.WEEKLY,
            intervalValue = type.daysBitmask,
            hour = type.hour,
            minute = type.minute,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        is RuleType.OneTime -> ScheduleRuleEntity(
            id = id,
            userId = userId,
            type = ScheduleRuleType.ONETIME,
            targetDate = type.date,
            hour = type.hour,
            minute = type.minute,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
