package com.personalcoacher.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for database migrations to ensure data integrity when upgrading.
 *
 * To run these tests:
 * 1. Enable schema export in build.gradle.kts by adding:
 *    ksp {
 *        arg("room.schemaLocation", "$projectDir/schemas")
 *    }
 * 2. Build the app to generate schema files
 * 3. Run tests: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PersonalCoachDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test migration from version 6 to 7.
     * This migration adds agenda_items and event_suggestions tables.
     */
    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        // Create database at version 6
        helper.createDatabase(testDbName, 6).apply {
            // Insert test data into existing tables if needed
            close()
        }

        // Run migration 6 -> 7
        val db = helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            PersonalCoachDatabase.Companion.MIGRATION_6_7
        )

        // Verify agenda_items table was created correctly
        val cursor = db.query("SELECT * FROM agenda_items")
        assert(cursor.columnCount == 12) { "agenda_items should have 12 columns" }
        cursor.close()

        // Verify event_suggestions table was created correctly
        val cursor2 = db.query("SELECT * FROM event_suggestions")
        assert(cursor2.columnCount == 12) { "event_suggestions should have 12 columns" }
        cursor2.close()

        // Verify indices were created
        val indexCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='agenda_items'")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(0))
        }
        indexCursor.close()
        assert(indexNames.contains("index_agenda_items_userId_startTime")) { "Missing index on agenda_items" }

        db.close()
    }

    /**
     * Test that all migrations run successfully from version 1 to current.
     */
    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create database at version 1
        helper.createDatabase(testDbName, 1).apply {
            close()
        }

        // Run all migrations
        helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            PersonalCoachDatabase.Companion.MIGRATION_1_2,
            PersonalCoachDatabase.Companion.MIGRATION_2_3,
            PersonalCoachDatabase.Companion.MIGRATION_3_4,
            PersonalCoachDatabase.Companion.MIGRATION_4_5,
            PersonalCoachDatabase.Companion.MIGRATION_5_6,
            PersonalCoachDatabase.Companion.MIGRATION_6_7
        ).close()
    }
}
