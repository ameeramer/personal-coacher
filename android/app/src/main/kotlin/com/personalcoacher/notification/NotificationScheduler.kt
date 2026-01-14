package com.personalcoacher.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
    // NOTE: This method now only triggers an immediate notification.
    // The periodic scheduling is handled by schedule rules in the database.
    fun scheduleDynamicNotifications() {
        debugLog.log(TAG, "scheduleDynamicNotifications() called")

        // Trigger an immediate one-time notification so user sees feedback
        triggerImmediateDynamicNotification()

        // NOTE: The default 6-hour periodic schedule is now managed via schedule rules
        // in the database, not via this hardcoded method. This allows users to see
        // and modify the default schedule in the UI.
        debugLog.log(TAG, "Dynamic notifications enabled - schedules managed via rules")
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

    /**
     * Reschedule a daily notification for the next day.
     * Called from DynamicNotificationWorker after successful delivery.
     */
    fun rescheduleDailyNotification(ruleId: String, hour: Int, minute: Int) {
        debugLog.log(TAG, "rescheduleDailyNotification: $ruleId at $hour:$minute")

        // Calculate the trigger time for tomorrow at the specified time
        val triggerTimeMs = calculateNextDayTriggerTime(hour, minute)
        val delayMinutes = (triggerTimeMs - System.currentTimeMillis()) / (1000 * 60)
        debugLog.log(TAG, "Next occurrence in ${delayMinutes} minutes")

        // Use AlarmManager for precise timing
        scheduleExactAlarm(
            ruleId = ruleId,
            triggerTimeMs = triggerTimeMs,
            rescheduleDaily = true,
            hour = hour,
            minute = minute
        )
    }

    private fun calculateNextDayTriggerTime(hour: Int, minute: Int): Long {
        val target = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1) // Tomorrow
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis
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

        // Cancel all existing rule-based workers first
        // Pass all rule IDs so we can properly cancel their alarms and workers
        cancelAllScheduleRuleWorkers(rules.map { it.id })

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

        // Cancel the alarm
        cancelExactAlarm(ruleId)

        // Also cancel any WorkManager work
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * Cancel all schedule rule workers by cancelling all work with the schedule rule prefix.
     * This also cancels any alarms associated with schedule rules.
     *
     * @param ruleIds List of rule IDs to cancel. All alarms and workers for these rules will be cancelled.
     */
    private fun cancelAllScheduleRuleWorkers(ruleIds: List<String>) {
        debugLog.log(TAG, "cancelAllScheduleRuleWorkers() called with ${ruleIds.size} rules")

        // Cancel all alarms and workers for each rule
        ruleIds.forEach { ruleId ->
            // Cancel the main alarm/worker
            cancelExactAlarm(ruleId)
            WorkManager.getInstance(context).cancelUniqueWork("$SCHEDULE_RULE_WORK_PREFIX$ruleId")

            // Also cancel any weekly day-specific workers (up to 6 additional days)
            for (dayIndex in 1..6) {
                val dayWorkName = "${SCHEDULE_RULE_WORK_PREFIX}${ruleId}_day_$dayIndex"
                WorkManager.getInstance(context).cancelUniqueWork(dayWorkName)
            }
        }

        debugLog.log(TAG, "Cancelled all existing schedule rule workers")
    }

    private fun scheduleIntervalRule(ruleId: String, interval: RuleType.Interval) {
        debugLog.log(TAG, "scheduleIntervalRule: ${interval.value} ${interval.unit}")

        // Calculate the delay in milliseconds
        val delayMs = when (interval.unit) {
            IntervalUnit.MINUTES -> interval.value.toLong() * 60 * 1000
            IntervalUnit.HOURS -> interval.value.toLong() * 60 * 60 * 1000
            IntervalUnit.DAYS -> interval.value.toLong() * 24 * 60 * 60 * 1000
            IntervalUnit.WEEKS -> interval.value.toLong() * 7 * 24 * 60 * 60 * 1000
            else -> interval.value.toLong() * 60 * 60 * 1000
        }

        val triggerTimeMs = System.currentTimeMillis() + delayMs
        val delayMinutes = delayMs / (1000 * 60)

        debugLog.log(TAG, "Scheduling interval alarm: $ruleId every ${interval.value} ${interval.unit}, first in ${delayMinutes} minutes")

        // Use AlarmManager for precise timing
        scheduleExactAlarm(
            ruleId = ruleId,
            triggerTimeMs = triggerTimeMs,
            rescheduleInterval = true,
            intervalValue = interval.value,
            intervalUnit = interval.unit
        )
    }

    private fun scheduleDailyRule(ruleId: String, daily: RuleType.Daily) {
        debugLog.log(TAG, "scheduleDailyRule: ${daily.hour}:${daily.minute}")

        // Calculate the exact trigger time
        val triggerTimeMs = calculateTriggerTime(daily.hour, daily.minute)
        val delayMinutes = (triggerTimeMs - System.currentTimeMillis()) / (1000 * 60)
        debugLog.log(TAG, "Calculated trigger time: $triggerTimeMs, delay: ${delayMinutes} minutes")

        // Use AlarmManager for precise timing
        scheduleExactAlarm(
            ruleId = ruleId,
            triggerTimeMs = triggerTimeMs,
            rescheduleDaily = true,
            hour = daily.hour,
            minute = daily.minute
        )

        debugLog.log(TAG, "Scheduled daily alarm for rule $ruleId at ${daily.hour}:${daily.minute}")
    }

    /**
     * Calculate the exact trigger time in milliseconds for a daily schedule.
     */
    private fun calculateTriggerTime(targetHour: Int, targetMinute: Int): Long {
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time has passed today, schedule for tomorrow
        if (targetTime.timeInMillis <= currentTime.timeInMillis) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1)
            debugLog.log(TAG, "Target time passed, scheduling for tomorrow")
        }

        return targetTime.timeInMillis
    }

    /**
     * Schedule an exact alarm using AlarmManager.
     * This provides more precise timing than WorkManager alone.
     */
    private fun scheduleExactAlarm(
        ruleId: String,
        triggerTimeMs: Long,
        rescheduleDaily: Boolean = false,
        rescheduleInterval: Boolean = false,
        hour: Int = 0,
        minute: Int = 0,
        intervalValue: Int = 0,
        intervalUnit: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(NotificationAlarmReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(NotificationAlarmReceiver.EXTRA_RESCHEDULE_DAILY, rescheduleDaily)
            putExtra(NotificationAlarmReceiver.EXTRA_RESCHEDULE_INTERVAL, rescheduleInterval)
            putExtra(NotificationAlarmReceiver.EXTRA_HOUR, hour)
            putExtra(NotificationAlarmReceiver.EXTRA_MINUTE, minute)
            putExtra(NotificationAlarmReceiver.EXTRA_INTERVAL_VALUE, intervalValue)
            putExtra(NotificationAlarmReceiver.EXTRA_INTERVAL_UNIT, intervalUnit)
        }

        // Generate a deterministic request code from the rule ID
        // Use a consistent hash that distributes well across the int space
        // and is less likely to collide than String.hashCode()
        val requestCode = generateRequestCode(ruleId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setExactAndAllowWhileIdle for precise timing even in Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+, check if we can schedule exact alarms
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    debugLog.log(TAG, "Scheduled exact alarm for $ruleId at ${java.util.Date(triggerTimeMs)}")
                } else {
                    // Fall back to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    debugLog.log(TAG, "Scheduled inexact alarm (no exact permission) for $ruleId")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                debugLog.log(TAG, "Scheduled exact alarm for $ruleId at ${java.util.Date(triggerTimeMs)}")
            }
        } catch (e: SecurityException) {
            debugLog.log(TAG, "SecurityException scheduling alarm: ${e.message}")
            // Fall back to WorkManager
            fallbackToWorkManager(ruleId, triggerTimeMs - System.currentTimeMillis(), rescheduleDaily, hour, minute, rescheduleInterval, intervalValue, intervalUnit)
        }
    }

    /**
     * Cancel an exact alarm.
     */
    private fun cancelExactAlarm(ruleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationAlarmReceiver::class.java)
        val requestCode = generateRequestCode(ruleId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            debugLog.log(TAG, "Cancelled exact alarm for $ruleId")
        }
    }

    /**
     * Generate a deterministic request code from a rule ID.
     * Uses a consistent hash algorithm (FNV-1a variant) that distributes well
     * across the int space and reduces collision probability compared to String.hashCode().
     */
    private fun generateRequestCode(ruleId: String): Int {
        // FNV-1a hash algorithm (32-bit)
        // This provides better distribution than String.hashCode() for UUID-like strings
        var hash = 0x811c9dc5.toInt() // FNV offset basis
        val fnvPrime = 0x01000193 // FNV prime

        for (char in ruleId) {
            hash = hash xor char.code
            hash *= fnvPrime
        }

        // Ensure we get a positive int (PendingIntent request codes should be positive for clarity)
        return hash and Int.MAX_VALUE
    }

    /**
     * Fall back to WorkManager if AlarmManager isn't available.
     */
    private fun fallbackToWorkManager(
        ruleId: String,
        delayMs: Long,
        rescheduleDaily: Boolean,
        hour: Int,
        minute: Int,
        rescheduleInterval: Boolean,
        intervalValue: Int,
        intervalUnit: String
    ) {
        debugLog.log(TAG, "Falling back to WorkManager for $ruleId")
        val workRequest = OneTimeWorkRequestBuilder<DynamicNotificationWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(createInputData(ruleId, rescheduleDaily, rescheduleInterval, hour, minute, intervalValue, intervalUnit))
            .build()

        val workName = "$SCHEDULE_RULE_WORK_PREFIX$ruleId"
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
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

        val triggerTimeMs = calculateOneTimeTriggerTime(oneTime.date, oneTime.hour, oneTime.minute)

        if (triggerTimeMs <= System.currentTimeMillis()) {
            debugLog.log(TAG, "One-time rule is in the past, skipping")
            return
        }

        debugLog.log(TAG, "Scheduling one-time alarm: $ruleId at ${java.util.Date(triggerTimeMs)}")

        // Use AlarmManager for precise timing
        scheduleExactAlarm(
            ruleId = ruleId,
            triggerTimeMs = triggerTimeMs
        )
    }

    private fun calculateOneTimeTriggerTime(dateStr: String, hour: Int, minute: Int): Long {
        return try {
            val targetDate = LocalDate.parse(dateStr)
            val targetTime = LocalTime.of(hour, minute)
            val targetDateTime = LocalDateTime.of(targetDate, targetTime)
            targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            debugLog.log(TAG, "Error parsing date: ${e.message}")
            0L
        }
    }

    private fun createInputData(
        ruleId: String,
        rescheduleDaily: Boolean = false,
        rescheduleInterval: Boolean = false,
        hour: Int = 0,
        minute: Int = 0,
        intervalValue: Int = 0,
        intervalUnit: String = ""
    ): Data {
        return Data.Builder()
            .putString("rule_id", ruleId)
            .putBoolean("reschedule_daily", rescheduleDaily)
            .putBoolean("reschedule_interval", rescheduleInterval)
            .putInt("hour", hour)
            .putInt("minute", minute)
            .putInt("interval_value", intervalValue)
            .putString("interval_unit", intervalUnit)
            .build()
    }

    /**
     * Reschedule an interval notification.
     * Called from DynamicNotificationWorker after successful delivery.
     */
    fun rescheduleIntervalNotification(ruleId: String, intervalValue: Int, intervalUnit: String) {
        debugLog.log(TAG, "rescheduleIntervalNotification: $ruleId every $intervalValue $intervalUnit")

        // Calculate the delay in milliseconds
        val delayMs = when (intervalUnit) {
            IntervalUnit.MINUTES -> intervalValue.toLong() * 60 * 1000
            IntervalUnit.HOURS -> intervalValue.toLong() * 60 * 60 * 1000
            IntervalUnit.DAYS -> intervalValue.toLong() * 24 * 60 * 60 * 1000
            IntervalUnit.WEEKS -> intervalValue.toLong() * 7 * 24 * 60 * 60 * 1000
            else -> intervalValue.toLong() * 60 * 60 * 1000
        }

        val triggerTimeMs = System.currentTimeMillis() + delayMs
        debugLog.log(TAG, "Next interval notification in ${delayMs/1000/60} minutes")

        // Use AlarmManager for precise timing
        scheduleExactAlarm(
            ruleId = ruleId,
            triggerTimeMs = triggerTimeMs,
            rescheduleInterval = true,
            intervalValue = intervalValue,
            intervalUnit = intervalUnit
        )
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
