package com.personalcoacher.di

import com.personalcoacher.data.repository.AuthRepositoryImpl
import com.personalcoacher.data.repository.ChatRepositoryImpl
import com.personalcoacher.data.repository.JournalRepositoryImpl
import com.personalcoacher.data.repository.SummaryRepositoryImpl
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.JournalRepository
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
}
