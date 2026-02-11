package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.Note
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getNotes(userId: String, limit: Int = 100): Flow<List<Note>>

    fun getNoteById(id: String): Flow<Note?>

    fun searchNotes(userId: String, query: String): Flow<List<Note>>

    suspend fun createNote(
        userId: String,
        title: String,
        content: String
    ): Resource<Note>

    suspend fun updateNote(
        id: String,
        title: String,
        content: String
    ): Resource<Note>

    suspend fun deleteNote(id: String): Resource<Unit>

    suspend fun syncNotes(userId: String): Resource<Unit>

    suspend fun uploadNotes(userId: String): Resource<Unit>
}
