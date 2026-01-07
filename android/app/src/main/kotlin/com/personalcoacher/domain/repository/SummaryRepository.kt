package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    fun getSummaries(userId: String, limit: Int = 20): Flow<List<Summary>>

    fun getSummariesByType(userId: String, type: SummaryType, limit: Int = 20): Flow<List<Summary>>

    fun getSummaryById(id: String): Flow<Summary?>

    suspend fun generateSummary(userId: String, type: SummaryType): Resource<Summary>

    suspend fun syncSummaries(userId: String): Resource<Unit>
}
