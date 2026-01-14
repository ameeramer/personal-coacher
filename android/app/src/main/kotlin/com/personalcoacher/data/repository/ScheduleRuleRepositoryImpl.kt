package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.ScheduleRuleDao
import com.personalcoacher.domain.model.ScheduleRule
import com.personalcoacher.domain.model.toDomainModel
import com.personalcoacher.domain.model.toEntity
import com.personalcoacher.domain.repository.ScheduleRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRuleRepositoryImpl @Inject constructor(
    private val scheduleRuleDao: ScheduleRuleDao
) : ScheduleRuleRepository {

    override fun getScheduleRules(userId: String): Flow<List<ScheduleRule>> {
        return scheduleRuleDao.getScheduleRulesByUser(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getEnabledScheduleRules(userId: String): Flow<List<ScheduleRule>> {
        return scheduleRuleDao.getEnabledScheduleRulesByUser(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override suspend fun getEnabledScheduleRulesSync(userId: String): List<ScheduleRule> {
        return scheduleRuleDao.getEnabledScheduleRulesSync(userId)
            .map { it.toDomainModel() }
    }

    override suspend fun getScheduleRuleById(id: String): ScheduleRule? {
        return scheduleRuleDao.getScheduleRuleById(id)?.toDomainModel()
    }

    override suspend fun addScheduleRule(rule: ScheduleRule) {
        scheduleRuleDao.insertScheduleRule(rule.toEntity())
    }

    override suspend fun updateScheduleRule(rule: ScheduleRule) {
        scheduleRuleDao.updateScheduleRule(rule.toEntity())
    }

    override suspend fun deleteScheduleRule(id: String) {
        scheduleRuleDao.deleteScheduleRuleById(id)
    }

    override suspend fun setScheduleRuleEnabled(id: String, enabled: Boolean) {
        scheduleRuleDao.setScheduleRuleEnabled(id, enabled)
    }

    override suspend fun hasScheduleRules(userId: String): Boolean {
        return scheduleRuleDao.getScheduleRuleCount(userId) > 0
    }
}
