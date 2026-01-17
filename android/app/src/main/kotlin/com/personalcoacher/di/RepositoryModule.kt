package com.personalcoacher.di

import com.personalcoacher.data.repository.AgendaRepositoryImpl
import com.personalcoacher.data.repository.AuthRepositoryImpl
import com.personalcoacher.data.repository.ChatRepositoryImpl
import com.personalcoacher.data.repository.DailyAppRepositoryImpl
import com.personalcoacher.data.repository.DynamicNotificationRepositoryImpl
import com.personalcoacher.data.repository.EventNotificationRepositoryImpl
import com.personalcoacher.data.repository.JournalRepositoryImpl
import com.personalcoacher.data.repository.RecorderRepositoryImpl
import com.personalcoacher.data.repository.ScheduleRuleRepositoryImpl
import com.personalcoacher.data.repository.SummaryRepositoryImpl
import com.personalcoacher.domain.repository.AgendaRepository
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.domain.repository.DynamicNotificationRepository
import com.personalcoacher.domain.repository.EventNotificationRepository
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.domain.repository.RecorderRepository
import com.personalcoacher.domain.repository.ScheduleRuleRepository
import com.personalcoacher.domain.repository.SummaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindSummaryRepository(impl: SummaryRepositoryImpl): SummaryRepository

    @Binds
    @Singleton
    abstract fun bindDynamicNotificationRepository(impl: DynamicNotificationRepositoryImpl): DynamicNotificationRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRuleRepository(impl: ScheduleRuleRepositoryImpl): ScheduleRuleRepository

    @Binds
    @Singleton
    abstract fun bindRecorderRepository(impl: RecorderRepositoryImpl): RecorderRepository

    @Binds
    @Singleton
    abstract fun bindAgendaRepository(impl: AgendaRepositoryImpl): AgendaRepository

    @Binds
    @Singleton
    abstract fun bindEventNotificationRepository(impl: EventNotificationRepositoryImpl): EventNotificationRepository

    @Binds
    @Singleton
    abstract fun bindDailyAppRepository(impl: DailyAppRepositoryImpl): DailyAppRepository
}
