package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.NoteDao
import com.personalcoacher.data.local.entity.NoteEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateNoteRequest
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.NoteRepository
import com.personalcoacher.notification.KuzuSyncScheduler
import com.personalcoacher.notification.KuzuSyncWorker
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val noteDao: NoteDao,
    private val kuzuSyncScheduler: KuzuSyncScheduler
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

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(userId, KuzuSyncWorker.SYNC_TYPE_NOTE)

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

            // Schedule RAG knowledge graph sync
            kuzuSyncScheduler.scheduleImmediateSync(updatedNote.userId, KuzuSyncWorker.SYNC_TYPE_NOTE)

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

    override suspend fun syncNotes(userId: String): Resource<Unit> {
        return try {
            // Download notes from server (manual download action)
            val response = api.getNotes()
            if (response.isSuccessful && response.body() != null) {
                val serverNotes = response.body()!!
                serverNotes.forEach { dto ->
                    noteDao.insertNote(
                        NoteEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )
                }
                Resource.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Resource.Error("Download failed: HTTP ${response.code()}\nError: $errorBody")
            }
        } catch (e: Exception) {
            Resource.Error("Download failed: ${e.localizedMessage}\nStack: ${e.stackTraceToString().take(500)}")
        }
    }

    override suspend fun uploadNotes(userId: String): Resource<Unit> {
        return try {
            // Upload all local-only notes to server (manual backup action)
            val localNotes = noteDao.getNotesBySyncStatus(SyncStatus.LOCAL_ONLY.name)
            var uploadedCount = 0
            var failedCount = 0
            val errorDetails = StringBuilder()

            errorDetails.appendLine("Notes to upload: ${localNotes.size}")

            for (note in localNotes) {
                try {
                    errorDetails.appendLine("\nUploading note: ${note.id} (${note.title})")
                    noteDao.updateSyncStatus(note.id, SyncStatus.SYNCING.name)

                    val request = CreateNoteRequest(
                        title = note.title,
                        content = note.content
                    )
                    errorDetails.appendLine("  Request: title='${note.title}'")

                    val response = api.createNote(request)
                    errorDetails.appendLine("  HTTP Status: ${response.code()}")

                    if (response.isSuccessful && response.body() != null) {
                        val serverNote = response.body()!!
                        errorDetails.appendLine("  SUCCESS: Server returned note id=${serverNote.id}")
                        noteDao.deleteNote(note.id)
                        noteDao.insertNote(
                            NoteEntity.fromDomainModel(
                                serverNote.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                            )
                        )
                        uploadedCount++
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "No error body"
                        errorDetails.appendLine("  FAILED: HTTP ${response.code()}")
                        errorDetails.appendLine("  Error body: $errorBody")
                        noteDao.updateSyncStatus(note.id, SyncStatus.LOCAL_ONLY.name)
                        failedCount++
                    }
                } catch (e: Exception) {
                    errorDetails.appendLine("  EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
                    errorDetails.appendLine("  Stack: ${e.stackTraceToString().take(300)}")
                    noteDao.updateSyncStatus(note.id, SyncStatus.LOCAL_ONLY.name)
                    failedCount++
                }
            }

            if (failedCount > 0) {
                Resource.Error("Uploaded $uploadedCount notes, $failedCount failed\n\n--- Details ---\n$errorDetails")
            } else if (uploadedCount == 0) {
                Resource.Success(Unit) // Nothing to upload
            } else {
                Resource.Success(Unit)
            }
        } catch (e: Exception) {
            Resource.Error("Backup failed: ${e.localizedMessage}\nStack: ${e.stackTraceToString().take(500)}")
        }
    }
}
