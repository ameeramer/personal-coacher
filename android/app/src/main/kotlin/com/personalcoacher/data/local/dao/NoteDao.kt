package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun getNotesForUser(userId: String, limit: Int = 100): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getNotesForUserSync(userId: String, limit: Int = 100): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteByIdSync(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE syncStatus = :status")
    suspend fun getNotesBySyncStatus(status: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Query("DELETE FROM notes WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId")
    suspend fun getNoteCount(userId: String): Int

    @Query("SELECT * FROM notes WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchNotes(userId: String, query: String): Flow<List<NoteEntity>>
}
