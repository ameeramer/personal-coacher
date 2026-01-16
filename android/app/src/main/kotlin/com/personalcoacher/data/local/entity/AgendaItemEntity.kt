package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "agenda_items",
    indices = [
        Index(value = ["userId", "startTime"]),
        Index(value = ["syncStatus"]),
        Index(value = ["sourceJournalEntryId"])
    ]
)
data class AgendaItemEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long?,
    val isAllDay: Boolean,
    val location: String?,
    val sourceJournalEntryId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(): AgendaItem {
        return AgendaItem(
            id = id,
            userId = userId,
            title = title,
            description = description,
            startTime = Instant.ofEpochMilli(startTime),
            endTime = endTime?.let { Instant.ofEpochMilli(it) },
            isAllDay = isAllDay,
            location = location,
            sourceJournalEntryId = sourceJournalEntryId,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(item: AgendaItem): AgendaItemEntity {
            return AgendaItemEntity(
                id = item.id,
                userId = item.userId,
                title = item.title,
                description = item.description,
                startTime = item.startTime.toEpochMilli(),
                endTime = item.endTime?.toEpochMilli(),
                isAllDay = item.isAllDay,
                location = item.location,
                sourceJournalEntryId = item.sourceJournalEntryId,
                createdAt = item.createdAt.toEpochMilli(),
                updatedAt = item.updatedAt.toEpochMilli(),
                syncStatus = item.syncStatus.name
            )
        }
    }
}
