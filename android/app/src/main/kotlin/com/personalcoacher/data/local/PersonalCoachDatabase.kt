package com.personalcoacher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.ScheduleRuleDao
import com.personalcoacher.data.local.dao.SentNotificationDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.ConversationEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.local.entity.ScheduleRuleEntity
import com.personalcoacher.data.local.entity.SentNotificationEntity
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        JournalEntryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SummaryEntity::class,
        SentNotificationEntity::class,
        ScheduleRuleEntity::class
    ],
    version = 4,
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
    }
}
