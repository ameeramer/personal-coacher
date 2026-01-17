package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.personalcoacher.domain.model.DailyAppData
import com.personalcoacher.domain.model.SyncStatus
import java.time.Instant

/**
 * Room entity for schema-less key-value storage for AI-generated apps.
 * Each app can store arbitrary data using JSON-serialized values.
 * This enables persistence for any data structure the AI-generated app needs.
 */
@Entity(
    tableName = "daily_app_data",
    primaryKeys = ["appId", "key"],
    indices = [
        Index(value = ["appId"]),
        Index(value = ["syncStatus"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DailyAppEntity::class,
            parentColumns = ["id"],
            childColumns = ["appId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DailyAppDataEntity(
    val appId: String,                 // Links to DailyAppEntity
    val key: String,                   // Arbitrary key name defined by the app
    val value: String,                 // JSON-serialized value (can be any structure)
    val updatedAt: Long,
    val syncStatus: String             // SyncStatus: LOCAL_ONLY, SYNCING, SYNCED
) {
    fun toDomainModel(): DailyAppData {
        return DailyAppData(
            appId = appId,
            key = key,
            value = value,
            updatedAt = Instant.ofEpochMilli(updatedAt),
            syncStatus = SyncStatus.valueOf(syncStatus)
        )
    }

    companion object {
        fun fromDomainModel(data: DailyAppData): DailyAppDataEntity {
            return DailyAppDataEntity(
                appId = data.appId,
                key = data.key,
                value = data.value,
                updatedAt = data.updatedAt.toEpochMilli(),
                syncStatus = data.syncStatus.name
            )
        }
    }
}
