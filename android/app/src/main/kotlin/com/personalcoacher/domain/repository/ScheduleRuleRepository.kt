package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.ScheduleRule
import kotlinx.coroutines.flow.Flow

interface ScheduleRuleRepository {
    fun getScheduleRules(userId: String): Flow<List<ScheduleRule>>
    fun getEnabledScheduleRules(userId: String): Flow<List<ScheduleRule>>
    suspend fun getEnabledScheduleRulesSync(userId: String): List<ScheduleRule>
    suspend fun getScheduleRuleById(id: String): ScheduleRule?
    suspend fun addScheduleRule(rule: ScheduleRule)
    suspend fun updateScheduleRule(rule: ScheduleRule)
    suspend fun deleteScheduleRule(id: String)
    suspend fun setScheduleRuleEnabled(id: String, enabled: Boolean)
    suspend fun hasScheduleRules(userId: String): Boolean
}
