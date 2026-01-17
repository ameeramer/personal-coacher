package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.EventNotification
import com.personalcoacher.domain.model.EventNotificationAnalysis
import com.personalcoacher.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing event notifications.
 */
interface EventNotificationRepository {
    /**
     * Get notification settings for a specific agenda item.
     */
    fun getNotificationForAgendaItem(agendaItemId: String): Flow<EventNotification?>

    /**
     * Get all pending notifications for a user.
     */
    suspend fun getPendingNotifications(userId: String): List<EventNotification>

    /**
     * Analyze an agenda item with AI to determine notification settings.
     */
    suspend fun analyzeAgendaItem(
        agendaItemId: String,
        title: String,
        description: String?,
        startTime: Long,
        endTime: Long?,
        isAllDay: Boolean,
        location: String?
    ): Result<EventNotificationAnalysis>

    /**
     * Save notification settings for an agenda item.
     */
    suspend fun saveNotificationSettings(
        agendaItemId: String,
        userId: String,
        notifyBefore: Boolean,
        minutesBefore: Int?,
        beforeMessage: String?,
        notifyAfter: Boolean,
        minutesAfter: Int?,
        afterMessage: String?,
        aiDetermined: Boolean,
        aiReasoning: String?
    ): Result<EventNotification>

    /**
     * Update notification settings manually.
     */
    suspend fun updateNotificationSettings(
        agendaItemId: String,
        notifyBefore: Boolean?,
        minutesBefore: Int?,
        notifyAfter: Boolean?,
        minutesAfter: Int?
    ): Result<EventNotification>

    /**
     * Delete notification settings for an agenda item.
     */
    suspend fun deleteNotificationSettings(agendaItemId: String): Result<Unit>

    /**
     * Mark a before notification as sent.
     */
    suspend fun markBeforeNotificationSent(notificationId: String)

    /**
     * Mark an after notification as sent.
     */
    suspend fun markAfterNotificationSent(notificationId: String)
}
