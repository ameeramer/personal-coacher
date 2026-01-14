package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "sent_notifications",
    indices = [
        Index(value = ["userId", "sentAt"])
    ]
)
data class SentNotificationEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val topicReference: String?, // The journal topic this notification referenced
    val timeOfDay: String, // 'morning', 'afternoon', 'evening', 'night'
    val sentAt: Long
) {
    companion object {
        fun create(
            userId: String,
            title: String,
            body: String,
            topicReference: String?,
            timeOfDay: String
        ): SentNotificationEntity {
            return SentNotificationEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                body = body,
                topicReference = topicReference,
                timeOfDay = timeOfDay,
                sentAt = Instant.now().toEpochMilli()
            )
        }
    }
}
