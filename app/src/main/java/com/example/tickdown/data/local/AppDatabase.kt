package com.example.tickdown.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tickdown.data.local.entity.CountdownEventEntity
import com.example.tickdown.data.local.entity.ReminderDeliveryEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE countdown_events ADD COLUMN remindersOffsets TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reminder_deliveries (
                eventId INTEGER NOT NULL,
                reminderOffsetMinutes INTEGER NOT NULL,
                occurrenceEpochSecond INTEGER NOT NULL,
                deliveredAt INTEGER NOT NULL,
                PRIMARY KEY(eventId, reminderOffsetMinutes, occurrenceEpochSecond)
            )
        """.trimIndent())
    }
}

@Database(
    entities = [CountdownEventEntity::class, ReminderDeliveryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun countdownDao(): CountdownDao
    abstract fun reminderDeliveryDao(): ReminderDeliveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tickdown_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
