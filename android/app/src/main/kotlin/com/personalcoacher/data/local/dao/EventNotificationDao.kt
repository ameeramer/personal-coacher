package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.EventNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventNotificationDao {
    @Query("SELECT * FROM event_notifications WHERE userId = :userId")
    fun getNotificationsForUser(userId: String): Flow<List<EventNotificationEntity>>

    @Query("SELECT * FROM event_notifications WHERE agendaItemId = :agendaItemId")
    fun getNotificationForAgendaItem(agendaItemId: String): Flow<EventNotificationEntity?>

    @Query("SELECT * FROM event_notifications WHERE agendaItemId = :agendaItemId")
    suspend fun getNotificationForAgendaItemSync(agendaItemId: String): EventNotificationEntity?

    @Query("SELECT * FROM event_notifications WHERE id = :id")
    suspend fun getNotificationById(id: String): EventNotificationEntity?

    /**
     * Get pending before notifications that need to be sent.
     * Returns notifications where:
     * - notifyBefore is true
     * - beforeNotificationSent is false
     * - The event start time minus minutesBefore is in the past (it's time to notify)
     */
    @Query("""
        SELECT en.* FROM event_notifications en
        INNER JOIN agenda_items ai ON en.agendaItemId = ai.id
        WHERE en.userId = :userId
        AND en.notifyBefore = 1
        AND en.beforeNotificationSent = 0
        AND en.minutesBefore IS NOT NULL
        AND (ai.startTime - (en.minutesBefore * 60 * 1000)) <= :currentTimeMs
        AND ai.startTime > :currentTimeMs
    """)
    suspend fun getPendingBeforeNotifications(userId: String, currentTimeMs: Long): List<EventNotificationEntity>

    /**
     * Get pending after notifications that need to be sent.
     * Returns notifications where:
     * - notifyAfter is true
     * - afterNotificationSent is false
     * - The event end time (or start time) plus minutesAfter is in the past (it's time to notify)
     */
    @Query("""
        SELECT en.* FROM event_notifications en
        INNER JOIN agenda_items ai ON en.agendaItemId = ai.id
        WHERE en.userId = :userId
        AND en.notifyAfter = 1
        AND en.afterNotificationSent = 0
        AND en.minutesAfter IS NOT NULL
        AND (COALESCE(ai.endTime, ai.startTime) + (en.minutesAfter * 60 * 1000)) <= :currentTimeMs
        AND (COALESCE(ai.endTime, ai.startTime) + (en.minutesAfter * 60 * 1000) + (30 * 60 * 1000)) > :currentTimeMs
    """)
    suspend fun getPendingAfterNotifications(userId: String, currentTimeMs: Long): List<EventNotificationEntity>

    /**
     * Get upcoming notifications that need to be scheduled.
     * Returns notifications where before or after is enabled and not yet sent.
     */
    @Query("""
        SELECT en.* FROM event_notifications en
        INNER JOIN agenda_items ai ON en.agendaItemId = ai.id
        WHERE en.userId = :userId
        AND ai.startTime > :currentTimeMs
        AND (
            (en.notifyBefore = 1 AND en.beforeNotificationSent = 0)
            OR (en.notifyAfter = 1 AND en.afterNotificationSent = 0)
        )
        ORDER BY ai.startTime ASC
        LIMIT :limit
    """)
    suspend fun getUpcomingNotifications(userId: String, currentTimeMs: Long, limit: Int = 20): List<EventNotificationEntity>

    @Query("SELECT * FROM event_notifications WHERE syncStatus = :status")
    suspend fun getNotificationsBySyncStatus(status: String): List<EventNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: EventNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<EventNotificationEntity>)

    @Update
    suspend fun updateNotification(notification: EventNotificationEntity)

    @Query("UPDATE event_notifications SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("UPDATE event_notifications SET beforeNotificationSent = 1, beforeSentAt = :sentAt WHERE id = :id")
    suspend fun markBeforeNotificationSent(id: String, sentAt: Long)

    @Query("UPDATE event_notifications SET afterNotificationSent = 1, afterSentAt = :sentAt WHERE id = :id")
    suspend fun markAfterNotificationSent(id: String, sentAt: Long)

    @Query("DELETE FROM event_notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM event_notifications WHERE agendaItemId = :agendaItemId")
    suspend fun deleteNotificationForAgendaItem(agendaItemId: String)

    @Query("DELETE FROM event_notifications WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM event_notifications WHERE userId = :userId")
    suspend fun getNotificationCount(userId: String): Int
}
