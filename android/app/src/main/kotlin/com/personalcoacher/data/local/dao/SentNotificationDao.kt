package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalcoacher.data.local.entity.SentNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SentNotificationDao {
    @Query("SELECT * FROM sent_notifications WHERE userId = :userId ORDER BY sentAt DESC LIMIT :limit")
    fun getNotificationsForUser(userId: String, limit: Int = 20): Flow<List<SentNotificationEntity>>

    @Query("SELECT * FROM sent_notifications WHERE userId = :userId ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getRecentNotificationsSync(userId: String, limit: Int = 10): List<SentNotificationEntity>

    @Query("SELECT * FROM sent_notifications WHERE userId = :userId AND sentAt >= :sinceTimestamp ORDER BY sentAt DESC")
    suspend fun getNotificationsSince(userId: String, sinceTimestamp: Long): List<SentNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: SentNotificationEntity)

    @Query("DELETE FROM sent_notifications WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM sent_notifications WHERE sentAt < :beforeTimestamp")
    suspend fun deleteOldNotifications(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM sent_notifications WHERE userId = :userId")
    suspend fun getNotificationCount(userId: String): Int
}
