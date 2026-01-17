package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.EventNotification
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

/**
 * Entity representing notification settings for agenda items.
 * Stores AI-determined or user-configured notification preferences
 * for sending reminders before and after events.
 */
@Entity(
    tableName = "event_notifications",
    foreignKeys = [
        ForeignKey(
            entity = AgendaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["agendaItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["agendaItemId"], unique = true),
        Index(value = ["userId", "notifyBefore", "beforeNotificationSent"]),
        Index(value = ["userId", "notifyAfter", "afterNotificationSent"]),
        Index(value = ["syncStatus"])
    ]
)
data class EventNotificationEntity(
    @PrimaryKey
    val id: String,
    val agendaItemId: String,
    val userId: String,

    // Before-event notification settings
    val notifyBefore: Boolean = false,
    val minutesBefore: Int? = null,
    val beforeMessage: String? = null,
    val beforeNotificationSent: Boolean = false,
    val beforeSentAt: Long? = null,

    // After-event notification settings
    val notifyAfter: Boolean = false,
    val minutesAfter: Int? = null,
    val afterMessage: String? = null,
    val afterNotificationSent: Boolean = false,
    val afterSentAt: Long? = null,

    // AI analysis metadata
    val aiDetermined: Boolean = true,
    val aiReasoning: String? = null,

    // Timestamps
    val createdAt: Long,
    val updatedAt: Long,

    // Sync status for offline-first
    val syncStatus: String
) {
    fun toDomainModel(): EventNotification {
        return EventNotification(
            id = id,
            agendaItemId = agendaItemId,
            userId = userId,
            notifyBefore = notifyBefore,
            minutesBefore = minutesBefore,
            beforeMessage = beforeMessage,
            beforeNotificationSent = beforeNotificationSent,
            beforeSentAt = beforeSentAt?.let { Instant.ofEpochMilli(it) },
            notifyAfter = notifyAfter,
            minutesAfter = minutesAfter,
            afterMessage = afterMessage,
            afterNotificationSent = afterNotificationSent,
            afterSentAt = afterSentAt?.let { Instant.ofEpochMilli(it) },
            aiDetermined = aiDetermined,
            aiReasoning = aiReasoning,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(notification: EventNotification): EventNotificationEntity {
            return EventNotificationEntity(
                id = notification.id,
                agendaItemId = notification.agendaItemId,
                userId = notification.userId,
                notifyBefore = notification.notifyBefore,
                minutesBefore = notification.minutesBefore,
                beforeMessage = notification.beforeMessage,
                beforeNotificationSent = notification.beforeNotificationSent,
                beforeSentAt = notification.beforeSentAt?.toEpochMilli(),
                notifyAfter = notification.notifyAfter,
                minutesAfter = notification.minutesAfter,
                afterMessage = notification.afterMessage,
                afterNotificationSent = notification.afterNotificationSent,
                afterSentAt = notification.afterSentAt?.toEpochMilli(),
                aiDetermined = notification.aiDetermined,
                aiReasoning = notification.aiReasoning,
                createdAt = notification.createdAt.toEpochMilli(),
                updatedAt = notification.updatedAt.toEpochMilli(),
                syncStatus = notification.syncStatus.name
            )
        }
    }
}
