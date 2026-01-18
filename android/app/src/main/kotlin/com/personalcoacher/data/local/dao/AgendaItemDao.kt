package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.AgendaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaItemDao {
    @Query("SELECT * FROM agenda_items WHERE userId = :userId ORDER BY startTime ASC")
    fun getItemsForUser(userId: String): Flow<List<AgendaItemEntity>>

    @Query("SELECT * FROM agenda_items WHERE userId = :userId AND startTime >= :startTime AND startTime <= :endTime ORDER BY startTime ASC")
    fun getItemsInRange(userId: String, startTime: Long, endTime: Long): Flow<List<AgendaItemEntity>>

    @Query("SELECT * FROM agenda_items WHERE userId = :userId AND startTime >= :now ORDER BY startTime ASC LIMIT :limit")
    fun getUpcomingItems(userId: String, now: Long, limit: Int = 10): Flow<List<AgendaItemEntity>>

    @Query("SELECT * FROM agenda_items WHERE userId = :userId AND startTime >= :now ORDER BY startTime ASC LIMIT :limit")
    suspend fun getUpcomingItemsSync(userId: String, now: Long, limit: Int = 10): List<AgendaItemEntity>

    @Query("SELECT * FROM agenda_items WHERE id = :id")
    fun getItemById(id: String): Flow<AgendaItemEntity?>

    @Query("SELECT * FROM agenda_items WHERE id = :id")
    suspend fun getItemByIdSync(id: String): AgendaItemEntity?

    @Query("SELECT * FROM agenda_items WHERE syncStatus = :status")
    suspend fun getItemsBySyncStatus(status: String): List<AgendaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: AgendaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<AgendaItemEntity>)

    @Update
    suspend fun updateItem(item: AgendaItemEntity)

    @Query("UPDATE agenda_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM agenda_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM agenda_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM agenda_items WHERE userId = :userId")
    suspend fun getItemCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM agenda_items WHERE userId = :userId AND startTime >= :now")
    suspend fun getUpcomingItemCount(userId: String, now: Long): Int

    @Query("SELECT * FROM agenda_items WHERE userId = :userId ORDER BY startTime ASC")
    suspend fun getItemsForUserSync(userId: String): List<AgendaItemEntity>
}
