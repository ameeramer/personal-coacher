package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.personalcoacher.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getConversationsForUser(userId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getConversationById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationByIdSync(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE syncStatus = :status")
    suspend fun getConversationsBySyncStatus(status: String): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
