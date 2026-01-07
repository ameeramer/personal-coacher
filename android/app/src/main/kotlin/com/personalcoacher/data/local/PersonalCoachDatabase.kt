package com.personalcoacher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.dao.SummaryDao
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.ConversationEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.local.entity.SummaryEntity
import com.personalcoacher.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        JournalEntryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SummaryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PersonalCoachDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun summaryDao(): SummaryDao

    companion object {
        const val DATABASE_NAME = "personal_coacher_db"
    }
}
