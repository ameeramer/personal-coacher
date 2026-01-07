package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

@Entity(
    tableName = "summaries",
    indices = [
        Index(value = ["userId", "type", "startDate"]),
        Index(value = ["syncStatus"])
    ]
)
data class SummaryEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val content: String,
    val startDate: Long,
    val endDate: Long,
    val createdAt: Long,
    val syncStatus: String
) {
    fun toDomainModel(): Summary {
        return Summary(
            id = id,
            userId = userId,
            type = SummaryType.fromString(type),
            content = content,
            startDate = Instant.ofEpochMilli(startDate),
            endDate = Instant.ofEpochMilli(endDate),
            createdAt = Instant.ofEpochMilli(createdAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(summary: Summary): SummaryEntity {
            return SummaryEntity(
                id = summary.id,
                userId = summary.userId,
                type = summary.type.toApiString(),
                content = summary.content,
                startDate = summary.startDate.toEpochMilli(),
                endDate = summary.endDate.toEpochMilli(),
                createdAt = summary.createdAt.toEpochMilli(),
                syncStatus = summary.syncStatus.name
            )
        }
    }
}
