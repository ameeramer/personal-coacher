package com.personalcoacher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.DailyAppDao
import com.personalcoacher.data.local.dao.EventNotificationDao
import com.personalcoacher.data.local.dao.EventSuggestionDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.RecordingSessionDao
import com.personalcoacher.data.local.dao.ScheduleRuleDao
import com.personalcoacher.data.local.dao.SentNotificationDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.dao.TranscriptionDao
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.ConversationEntity
import com.personalcoacher.data.local.entity.DailyAppDataEntity
import com.personalcoacher.data.local.entity.DailyAppEntity
import com.personalcoacher.data.local.entity.EventNotificationEntity
import com.personalcoacher.data.local.entity.EventSuggestionEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.local.entity.RecordingSessionEntity
import com.personalcoacher.data.local.entity.ScheduleRuleEntity
import com.personalcoacher.data.local.entity.SentNotificationEntity
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.local.entity.TranscriptionEntity
import com.personalcoacher.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        JournalEntryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SummaryEntity::class,
        SentNotificationEntity::class,
        ScheduleRuleEntity::class,
        RecordingSessionEntity::class,
        TranscriptionEntity::class,
        AgendaItemEntity::class,
        EventSuggestionEntity::class,
        EventNotificationEntity::class,
        DailyAppEntity::class,
        DailyAppDataEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class PersonalCoachDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun summaryDao(): SummaryDao
    abstract fun sentNotificationDao(): SentNotificationDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun agendaItemDao(): AgendaItemDao
    abstract fun eventSuggestionDao(): EventSuggestionDao
    abstract fun eventNotificationDao(): EventNotificationDao
    abstract fun dailyAppDao(): DailyAppDao

    companion object {
        const val DATABASE_NAME = "personal_coacher_db"

        /**
         * Migration from version 1 to 2: Add sent_notifications table
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sent_notifications (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        topicReference TEXT,
                        timeOfDay TEXT NOT NULL,
                        sentAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // Create index for userId + sentAt queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sent_notifications_userId_sentAt ON sent_notifications(userId, sentAt)")
            }
        }

        /**
         * Migration from version 2 to 3: Add schedule_rules table
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS schedule_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        intervalValue INTEGER NOT NULL DEFAULT 0,
                        intervalUnit TEXT NOT NULL DEFAULT '',
                        hour INTEGER NOT NULL DEFAULT 0,
                        minute INTEGER NOT NULL DEFAULT 0,
                        targetDate TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from version 3 to 4: Add notificationSent column to messages table
         * This enables background AI processing with notifications when user leaves app
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add notificationSent column with default true (existing messages are considered "seen")
                db.execSQL("ALTER TABLE messages ADD COLUMN notificationSent INTEGER NOT NULL DEFAULT 1")
                // Add index for efficient notification queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_notificationSent ON messages(notificationSent)")
            }
        }

        /**
         * Migration from version 4 to 5: Add recording_sessions and transcriptions tables
         * This enables audio recording with chunked transcription via Gemini
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create recording_sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recording_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        title TEXT,
                        chunkDuration INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // Create indices for recording_sessions
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_sessions_userId_status ON recording_sessions(userId, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_sessions_userId_createdAt ON recording_sessions(userId, createdAt)")

                // Create transcriptions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transcriptions (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        chunkIndex INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (sessionId) REFERENCES recording_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Create indices for transcriptions
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transcriptions_sessionId_chunkIndex ON transcriptions(sessionId, chunkIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transcriptions_status ON transcriptions(status)")
            }
        }

        /**
         * Migration from version 5 to 6: Add audioFilePath column to transcriptions table
         * This enables retry functionality for failed transcriptions
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add audioFilePath column to store audio file path for retry
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN audioFilePath TEXT")
            }
        }

        /**
         * Migration from version 6 to 7: Add agenda_items and event_suggestions tables
         * This enables calendar/agenda feature with AI-detected event suggestions
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create agenda_items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS agenda_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        isAllDay INTEGER NOT NULL DEFAULT 0,
                        location TEXT,
                        sourceJournalEntryId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL
                    )
                """.trimIndent())
                // Create indices for agenda_items
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agenda_items_userId_startTime ON agenda_items(userId, startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agenda_items_syncStatus ON agenda_items(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agenda_items_sourceJournalEntryId ON agenda_items(sourceJournalEntryId)")

                // Create event_suggestions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_suggestions (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        journalEntryId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        suggestedStartTime INTEGER NOT NULL,
                        suggestedEndTime INTEGER,
                        isAllDay INTEGER NOT NULL DEFAULT 0,
                        location TEXT,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        processedAt INTEGER,
                        FOREIGN KEY (journalEntryId) REFERENCES journal_entries(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Create indices for event_suggestions
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_suggestions_userId_status ON event_suggestions(userId, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_suggestions_journalEntryId ON event_suggestions(journalEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_suggestions_createdAt ON event_suggestions(createdAt)")
            }
        }

        /**
         * Migration from version 7 to 8: Add event_notifications table
         * This enables dynamic notification times for agenda items
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create event_notifications table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_notifications (
                        id TEXT NOT NULL PRIMARY KEY,
                        agendaItemId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        notifyBefore INTEGER NOT NULL DEFAULT 0,
                        minutesBefore INTEGER,
                        beforeMessage TEXT,
                        beforeNotificationSent INTEGER NOT NULL DEFAULT 0,
                        beforeSentAt INTEGER,
                        notifyAfter INTEGER NOT NULL DEFAULT 0,
                        minutesAfter INTEGER,
                        afterMessage TEXT,
                        afterNotificationSent INTEGER NOT NULL DEFAULT 0,
                        afterSentAt INTEGER,
                        aiDetermined INTEGER NOT NULL DEFAULT 1,
                        aiReasoning TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL,
                        FOREIGN KEY (agendaItemId) REFERENCES agenda_items(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Create indices for event_notifications
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_event_notifications_agendaItemId ON event_notifications(agendaItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_notifications_userId_notifyBefore_beforeNotificationSent ON event_notifications(userId, notifyBefore, beforeNotificationSent)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_notifications_userId_notifyAfter_afterNotificationSent ON event_notifications(userId, notifyAfter, afterNotificationSent)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_notifications_syncStatus ON event_notifications(syncStatus)")
            }
        }

        /**
         * Migration from version 8 to 9: Add daily_apps and daily_app_data tables
         * This enables AI-generated dynamic web apps feature
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create daily_apps table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_apps (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        htmlCode TEXT NOT NULL,
                        journalContext TEXT,
                        status TEXT NOT NULL,
                        usedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL
                    )
                """.trimIndent())
                // Create indices for daily_apps
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_apps_userId_date ON daily_apps(userId, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_apps_userId_status ON daily_apps(userId, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_apps_syncStatus ON daily_apps(syncStatus)")

                // Create daily_app_data table (schema-less key-value storage)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_app_data (
                        appId TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL,
                        PRIMARY KEY (appId, key),
                        FOREIGN KEY (appId) REFERENCES daily_apps(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Create indices for daily_app_data
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_app_data_appId ON daily_app_data(appId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_app_data_syncStatus ON daily_app_data(syncStatus)")
            }
        }
    }
}
