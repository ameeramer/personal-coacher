package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    fun getLastMessageForConversation(conversationId: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun getMessageById(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageByIdSync(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = :status")
    suspend fun getMessagesByStatus(status: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE syncStatus = :status")
    suspend fun getMessagesBySyncStatus(status: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String, status: String, updatedAt: Long)

    @Query("UPDATE messages SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE status = 'pending' AND role = 'assistant' ORDER BY createdAt ASC")
    suspend fun getPendingAssistantMessages(): List<MessageEntity>

    @Query("UPDATE messages SET notificationSent = :sent WHERE id = :id")
    suspend fun updateNotificationSent(id: String, sent: Boolean)

    @Query("SELECT * FROM messages WHERE status = 'completed' AND role = 'assistant' AND notificationSent = 0 AND createdAt <= :threshold ORDER BY createdAt ASC")
    suspend fun getCompletedMessagesNeedingNotification(threshold: Long): List<MessageEntity>

    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.userId = :userId AND m.updatedAt > :since AND m.status = 'completed' ORDER BY m.updatedAt ASC")
    suspend fun getMessagesModifiedSince(userId: String, since: Long): List<MessageEntity>
}
