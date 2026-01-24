package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.NoteDao
import com.personalcoacher.data.local.entity.NoteEntity
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.NoteRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {

    override fun getNotes(userId: String, limit: Int): Flow<List<Note>> {
        return noteDao.getNotesForUser(userId, limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getNoteById(id: String): Flow<Note?> {
        return noteDao.getNoteById(id).map { it?.toDomainModel() }
    }

    override fun searchNotes(userId: String, query: String): Flow<List<Note>> {
        return noteDao.searchNotes(userId, query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun createNote(
        userId: String,
        title: String,
        content: String
    ): Resource<Note> {
        return try {
            val now = Instant.now()
            val note = Note(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                content = content,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            noteDao.insertNote(NoteEntity.fromDomainModel(note))
            Resource.Success(note)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create note")
        }
    }

    override suspend fun updateNote(
        id: String,
        title: String,
        content: String
    ): Resource<Note> {
        return try {
            val existingNote = noteDao.getNoteByIdSync(id)
                ?: return Resource.Error("Note not found")

            val updatedNote = existingNote.toDomainModel().copy(
                title = title,
                content = content,
                updatedAt = Instant.now(),
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            noteDao.updateNote(NoteEntity.fromDomainModel(updatedNote))
            Resource.Success(updatedNote)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update note")
        }
    }

    override suspend fun deleteNote(id: String): Resource<Unit> {
        return try {
            noteDao.deleteNote(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete note")
        }
    }
}
