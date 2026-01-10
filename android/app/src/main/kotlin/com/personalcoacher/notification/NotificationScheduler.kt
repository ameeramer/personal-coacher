package com.personalcoacher.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.entity.DayOfWeek
import com.personalcoacher.data.local.entity.IntervalUnit
import com.personalcoacher.domain.model.RuleType
import com.personalcoacher.domain.model.ScheduleRule
import com.personalcoacher.util.DebugLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper
) {
    companion object {
        private const val TAG = "NotificationScheduler"
        private const val SCHEDULE_RULE_WORK_PREFIX = "schedule_rule_"
    }

    fun scheduleJournalReminder() {
        val hour = tokenManager.getReminderHourSync()
        val minute = tokenManager.getReminderMinuteSync()
        debugLog.log(TAG, "scheduleJournalReminder() called - using stored time $hour:$minute")
        scheduleJournalReminder(hour, minute)
    }

    fun scheduleJournalReminder(hour: Int, minute: Int) {
        debugLog.log(TAG, "scheduleJournalReminder($hour, $minute) called")
        val initialDelay = calculateInitialDelay(hour, minute)
        val delayHours = initialDelay / (1000 * 60 * 60)
        val delayMinutes = (initialDelay / (1000 * 60)) % 60
        debugLog.log(TAG, "Initial delay: ${delayHours}h ${delayMinutes}m (${initialDelay}ms)")

        val workRequest = PeriodicWorkRequestBuilder<JournalReminderWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        debugLog.log(TAG, "Created PeriodicWorkRequest with ID=${workRequest.id}")
        debugLog.log(TAG, "Enqueuing work with UPDATE policy for '${JournalReminderWorker.WORK_NAME}'")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            JournalReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        debugLog.log(TAG, "Work enqueued successfully")
    }

    fun cancelJournalReminder() {
        debugLog.log(TAG, "cancelJournalReminder() called")
        WorkManager.getInstance(context).cancelUniqueWork(JournalReminderWorker.WORK_NAME)
        debugLog.log(TAG, "Work cancelled for '${JournalReminderWorker.WORK_NAME}'")
    }

    fun isReminderScheduled(): Boolean {
        debugLog.log(TAG, "isReminderScheduled() called")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(JournalReminderWorker.WORK_NAME)
            .get()

        val result = workInfos.any { !it.state.isFinished }
        debugLog.log(TAG, "isReminderScheduled() = $result (workInfos count: ${workInfos.size})")
        return result
    }

    fun getScheduledWorkInfo(): String {
        debugLog.log(TAG, "getScheduledWorkInfo() called")
        return try {
            val journalWorkInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(JournalReminderWorker.WORK_NAME)
                .get()

            val dynamicWorkInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(DynamicNotificationWorker.WORK_NAME)
                .get()

            val result = StringBuilder()

            result.appendLine("=== Journal Reminder ===")
            if (journalWorkInfos.isEmpty()) {
                result.appendLine("No scheduled work found")
            } else {
                journalWorkInfos.forEach { info ->
                    result.appendLine("Work ID: ${info.id}")
                    result.appendLine("State: ${info.state}")
                    result.appendLine("Run Attempt: ${info.runAttemptCount}")
                }
            }

            result.appendLine()
            result.appendLine("=== Dynamic Notifications ===")
            if (dynamicWorkInfos.isEmpty()) {
                result.appendLine("No scheduled work found")
            } else {
                dynamicWorkInfos.forEach { info ->
                    result.appendLine("Work ID: ${info.id}")
                    result.appendLine("State: ${info.state}")
                    result.appendLine("Run Attempt: ${info.runAttemptCount}")
                }
            }

            result.toString()
        } catch (e: Exception) {
            "Error getting work info: ${e.message}"
        }
    }

    // Dynamic notification scheduling (multiple times per day)
    fun scheduleDynamicNotifications() {
        debugLog.log(TAG, "scheduleDynamicNotifications() called")

        // First, trigger an immediate one-time notification so user sees feedback
        triggerImmediateDynamicNotification()

        // Schedule dynamic notifications every 6 hours
        // WorkManager will run the DynamicNotificationWorker periodically
        val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .build()

        debugLog.log(TAG, "Created PeriodicWorkRequest for dynamic notifications with ID=${workRequest.id}")
        debugLog.log(TAG, "Enqueuing work with UPDATE policy for '${DynamicNotificationWorker.WORK_NAME}'")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DynamicNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        debugLog.log(TAG, "Dynamic notification work enqueued successfully")
    }

    /**
     * Trigger an immediate one-time dynamic notification.
     * This is useful when the user first enables AI coach check-ins to provide immediate feedback.
     */
    fun triggerImmediateDynamicNotification() {
        debugLog.log(TAG, "triggerImmediateDynamicNotification() called")

        val workRequest = OneTimeWorkRequestBuilder<DynamicNotificationWorker>()
            .build()

        debugLog.log(TAG, "Created OneTimeWorkRequest for immediate notification with ID=${workRequest.id}")

        WorkManager.getInstance(context).enqueueUniqueWork(
            "immediate_dynamic_notification",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        debugLog.log(TAG, "Immediate dynamic notification work enqueued")
    }

    fun cancelDynamicNotifications() {
        debugLog.log(TAG, "cancelDynamicNotifications() called")
        WorkManager.getInstance(context).cancelUniqueWork(DynamicNotificationWorker.WORK_NAME)
        debugLog.log(TAG, "Work cancelled for '${DynamicNotificationWorker.WORK_NAME}'")
    }

    fun isDynamicNotificationsScheduled(): Boolean {
        debugLog.log(TAG, "isDynamicNotificationsScheduled() called")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DynamicNotificationWorker.WORK_NAME)
            .get()

        val result = workInfos.any { !it.state.isFinished }
        debugLog.log(TAG, "isDynamicNotificationsScheduled() = $result (workInfos count: ${workInfos.size})")
        return result
    }

    /**
     * Schedule notifications based on custom schedule rules.
     * This method handles all rule types: INTERVAL, DAILY, WEEKLY, ONETIME
     * The default 6-hour schedule is kept alongside custom rules.
     */
    fun scheduleFromRules(rules: List<ScheduleRule>) {
        debugLog.log(TAG, "scheduleFromRules() called with ${rules.size} rules")

        // Cancel all existing rule-based workers (but keep the default 6-hour schedule)
        cancelAllScheduleRuleWorkers()

        // Schedule each enabled rule
        rules.filter { it.enabled }.forEach { rule ->
            scheduleRule(rule)
        }
    }

    /**
     * Schedule a single rule
     */
    fun scheduleRule(rule: ScheduleRule) {
        debugLog.log(TAG, "scheduleRule() called for rule: ${rule.id}, type: ${rule.type}")

        when (rule.type) {
            is RuleType.Interval -> scheduleIntervalRule(rule.id, rule.type)
            is RuleType.Daily -> scheduleDailyRule(rule.id, rule.type)
            is RuleType.Weekly -> scheduleWeeklyRule(rule.id, rule.type)
            is RuleType.OneTime -> scheduleOneTimeRule(rule.id, rule.type)
        }
    }

    /**
     * Cancel a specific rule's scheduled work
     */
    fun cancelRule(ruleId: String) {
        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        debugLog.log(TAG, "cancelRule() called for $workName")
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * Cancel all schedule rule workers
     */
    private fun cancelAllScheduleRuleWorkers() {
        debugLog.log(TAG, "cancelAllScheduleRuleWorkers() called")
        // WorkManager doesn't have a way to cancel by prefix, so we need to track rule IDs
        // For now, we rely on the rules being passed to scheduleFromRules to manage this
        // New rules will replace old ones via ExistingPeriodicWorkPolicy.UPDATE
    }

    private fun scheduleIntervalRule(ruleId: String, interval: RuleType.Interval) {
        debugLog.log(TAG, "scheduleIntervalRule: ${interval.value} ${interval.unit}")

        val (repeatInterval, timeUnit) = when (interval.unit) {
            IntervalUnit.MINUTES -> Pair(interval.value.toLong(), TimeUnit.MINUTES)
            IntervalUnit.HOURS -> Pair(interval.value.toLong(), TimeUnit.HOURS)
            IntervalUnit.DAYS -> Pair(interval.value.toLong(), TimeUnit.DAYS)
            IntervalUnit.WEEKS -> Pair(interval.value.toLong() * 7, TimeUnit.DAYS)
            else -> Pair(interval.value.toLong(), TimeUnit.HOURS)
        }

        // WorkManager minimum interval is 15 minutes
        val effectiveInterval = if (timeUnit == TimeUnit.MINUTES && repeatInterval < 15) 15L else repeatInterval

        val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
            repeatInterval = effectiveInterval,
            repeatIntervalTimeUnit = timeUnit
        )
            .setInputData(createInputData(ruleId))
            .build()

        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        debugLog.log(TAG, "Scheduling interval work: $workName every $effectiveInterval ${timeUnit.name}")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleDailyRule(ruleId: String, daily: RuleType.Daily) {
        debugLog.log(TAG, "scheduleDailyRule: ${daily.hour}:${daily.minute}")

        val initialDelay = calculateInitialDelay(daily.hour, daily.minute)

        val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(createInputData(ruleId))
            .build()

        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        debugLog.log(TAG, "Scheduling daily work: $workName at ${daily.hour}:${daily.minute}")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleWeeklyRule(ruleId: String, weekly: RuleType.Weekly) {
        debugLog.log(TAG, "scheduleWeeklyRule: days=${weekly.daysBitmask}, time=${weekly.hour}:${weekly.minute}")

        // For weekly rules, we need to calculate the next occurrence
        val initialDelay = calculateWeeklyInitialDelay(weekly.daysBitmask, weekly.hour, weekly.minute)

        // Schedule weekly repeat (7 days)
        val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(createInputData(ruleId))
            .build()

        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        debugLog.log(TAG, "Scheduling weekly work: $workName")

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        // If multiple days are selected, we need additional workers for each day
        scheduleAdditionalWeeklyDays(ruleId, weekly)
    }

    private fun scheduleAdditionalWeeklyDays(ruleId: String, weekly: RuleType.Weekly) {
        // Get all selected days
        val selectedDays = mutableListOf<Int>()
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.MONDAY)) selectedDays.add(Calendar.MONDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.TUESDAY)) selectedDays.add(Calendar.TUESDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.WEDNESDAY)) selectedDays.add(Calendar.WEDNESDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.THURSDAY)) selectedDays.add(Calendar.THURSDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.FRIDAY)) selectedDays.add(Calendar.FRIDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.SATURDAY)) selectedDays.add(Calendar.SATURDAY)
        if (DayOfWeek.isDaySelected(weekly.daysBitmask, DayOfWeek.SUNDAY)) selectedDays.add(Calendar.SUNDAY)

        // Schedule a worker for each day (skip the first one, already scheduled above)
        selectedDays.drop(1).forEachIndexed { index, dayOfWeek ->
            val initialDelay = calculateDelayToNextDayOfWeek(dayOfWeek, weekly.hour, weekly.minute)

            val workRequest = PeriodicWorkRequestBuilder<DynamicNotificationWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(createInputData(ruleId))
                .build()

            val workName = "${SCHEDULE_RULE_WORK_PREFIX}${ruleId}_day_${index + 1}"
            debugLog.log(TAG, "Scheduling additional weekly work: $workName for day $dayOfWeek")

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    private fun scheduleOneTimeRule(ruleId: String, oneTime: RuleType.OneTime) {
        debugLog.log(TAG, "scheduleOneTimeRule: ${oneTime.date} at ${oneTime.hour}:${oneTime.minute}")

        val delay = calculateOneTimeDelay(oneTime.date, oneTime.hour, oneTime.minute)

        if (delay <= 0) {
            debugLog.log(TAG, "One-time rule is in the past, skipping")
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<DynamicNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(createInputData(ruleId))
            .build()

        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        debugLog.log(TAG, "Scheduling one-time work: $workName")

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun createInputData(ruleId: String): Data {
        return Data.Builder()
            .putString("rule_id", ruleId)
            .build()
    }

    private fun calculateWeeklyInitialDelay(daysBitmask: Int, hour: Int, minute: Int): Long {
        // Find the next occurrence of any of the selected days
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(hour, minute)

        var minDelay = Long.MAX_VALUE

        // Check each selected day
        listOf(
            DayOfWeek.MONDAY to java.time.DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY to java.time.DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY to java.time.DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY to java.time.DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY to java.time.DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY to java.time.DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY to java.time.DayOfWeek.SUNDAY
        ).forEach { (bitmaskDay, javaDayOfWeek) ->
            if (DayOfWeek.isDaySelected(daysBitmask, bitmaskDay)) {
                val delay = calculateDelayToNextDayOfWeek(
                    when (javaDayOfWeek) {
                        java.time.DayOfWeek.MONDAY -> Calendar.MONDAY
                        java.time.DayOfWeek.TUESDAY -> Calendar.TUESDAY
                        java.time.DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
                        java.time.DayOfWeek.THURSDAY -> Calendar.THURSDAY
                        java.time.DayOfWeek.FRIDAY -> Calendar.FRIDAY
                        java.time.DayOfWeek.SATURDAY -> Calendar.SATURDAY
                        java.time.DayOfWeek.SUNDAY -> Calendar.SUNDAY
                    },
                    hour,
                    minute
                )
                if (delay < minDelay) {
                    minDelay = delay
                }
            }
        }

        return if (minDelay == Long.MAX_VALUE) 0L else minDelay
    }

    private fun calculateDelayToNextDayOfWeek(targetDayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, targetDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target is in the past or today but time has passed, move to next week
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    private fun calculateOneTimeDelay(dateStr: String, hour: Int, minute: Int): Long {
        return try {
            val targetDate = LocalDate.parse(dateStr)
            val targetTime = LocalTime.of(hour, minute)
            val targetDateTime = LocalDateTime.of(targetDate, targetTime)
            val now = LocalDateTime.now()

            ChronoUnit.MILLIS.between(now, targetDateTime)
        } catch (e: Exception) {
            debugLog.log(TAG, "Error parsing date: ${e.message}")
            -1L
        }
    }

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        debugLog.log(TAG, "calculateInitialDelay: current=${formatCalendar(currentTime)}, target=${formatCalendar(targetTime)}")

        // If target time has passed today, schedule for tomorrow
        if (targetTime.before(currentTime) || targetTime.timeInMillis == currentTime.timeInMillis) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1)
            debugLog.log(TAG, "Target time passed, scheduling for tomorrow: ${formatCalendar(targetTime)}")
        }

        return targetTime.timeInMillis - currentTime.timeInMillis
    }

    private fun formatCalendar(cal: Calendar): String {
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}
