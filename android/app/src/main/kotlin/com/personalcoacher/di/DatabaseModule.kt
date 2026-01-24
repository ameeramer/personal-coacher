package com.personalcoacher.di

import android.content.Context
import androidx.room.Room
import com.personalcoacher.data.local.PersonalCoachDatabase
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.EventNotificationDao
import com.personalcoacher.data.local.dao.EventSuggestionDao
import com.personalcoacher.data.local.dao.GoalDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.NoteDao
import com.personalcoacher.data.local.dao.RecordingSessionDao
import com.personalcoacher.data.local.dao.ScheduleRuleDao
import com.personalcoacher.data.local.dao.SentNotificationDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.dao.TaskDao
import com.personalcoacher.data.local.dao.TranscriptionDao
import com.personalcoacher.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonalCoachDatabase {
        return Room.databaseBuilder(
            context,
            PersonalCoachDatabase::class.java,
            PersonalCoachDatabase.DATABASE_NAME
        )
            .addMigrations(
                PersonalCoachDatabase.MIGRATION_1_2,
                PersonalCoachDatabase.MIGRATION_2_3,
                PersonalCoachDatabase.MIGRATION_3_4,
                PersonalCoachDatabase.MIGRATION_4_5,
                PersonalCoachDatabase.MIGRATION_5_6,
                PersonalCoachDatabase.MIGRATION_6_7,
                PersonalCoachDatabase.MIGRATION_7_8,
                PersonalCoachDatabase.MIGRATION_8_9,
                PersonalCoachDatabase.MIGRATION_9_10
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: PersonalCoachDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideJournalEntryDao(database: PersonalCoachDatabase): JournalEntryDao {
        return database.journalEntryDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: PersonalCoachDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: PersonalCoachDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideSummaryDao(database: PersonalCoachDatabase): SummaryDao {
        return database.summaryDao()
    }

    @Provides
    @Singleton
    fun provideSentNotificationDao(database: PersonalCoachDatabase): SentNotificationDao {
        return database.sentNotificationDao()
    }

    @Provides
    @Singleton
    fun provideScheduleRuleDao(database: PersonalCoachDatabase): ScheduleRuleDao {
        return database.scheduleRuleDao()
    }

    @Provides
    @Singleton
    fun provideRecordingSessionDao(database: PersonalCoachDatabase): RecordingSessionDao {
        return database.recordingSessionDao()
    }

    @Provides
    @Singleton
    fun provideTranscriptionDao(database: PersonalCoachDatabase): TranscriptionDao {
        return database.transcriptionDao()
    }

    @Provides
    @Singleton
    fun provideAgendaItemDao(database: PersonalCoachDatabase): AgendaItemDao {
        return database.agendaItemDao()
    }

    @Provides
    @Singleton
    fun provideEventSuggestionDao(database: PersonalCoachDatabase): EventSuggestionDao {
        return database.eventSuggestionDao()
    }

    @Provides
    @Singleton
    fun provideEventNotificationDao(database: PersonalCoachDatabase): EventNotificationDao {
        return database.eventNotificationDao()
    }

    @Provides
    @Singleton
    fun provideDailyAppDao(database: PersonalCoachDatabase): DailyAppDao {
        return database.dailyAppDao()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: PersonalCoachDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideGoalDao(database: PersonalCoachDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: PersonalCoachDatabase): TaskDao {
        return database.taskDao()
    }
}
