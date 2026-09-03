package com.lime.touchlab.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lime.rawtouchcollector.SchemaFields

/**
 * Локальное хранилище завершённых попыток.
 * WAL включён по умолчанию: зафиксированная транзакция переживает убийство процесса.
 */
@Database(
    entities = [
        DeviceEntity::class,
        DisplayProfileEntity::class,
        SessionEntity::class,
        ClockSyncEntity::class,
        TrialEntity::class,
        TouchSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TouchLabDatabase : RoomDatabase() {

    abstract fun dao(): TouchLabDao

    companion object {
        private const val NAME: String = "touchlab.db"

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            private val added: List<String> = listOf(
                SchemaFields.TRIALS_ACCEPTED,
                SchemaFields.TRIALS_CONFIRMED,
                SchemaFields.QUEUE_OVERFLOWS,
                SchemaFields.WRITE_FAILURES,
                SchemaFields.EVENTS_BEFORE_START,
                SchemaFields.EVENTS_AFTER_END,
                SchemaFields.EVENTS_AFTER_SESSION_CLOSE,
                SchemaFields.EVENTS_DISCARDED_AFTER_MULTITOUCH,
                SchemaFields.IMPLICIT_CANCELS,
                SchemaFields.MULTITOUCH_ERRORS,
                SchemaFields.TRIALS_WITH_STALE_DISPLAY_PROFILE,
                SchemaFields.CLOCK_SYNC_FALLBACKS,
            )

            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in added) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN " + column + " INTEGER")
                }
            }
        }

        @Volatile
        private var instance: TouchLabDatabase? = null

        fun get(context: Context): TouchLabDatabase {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                val second = instance
                if (second != null) {
                    second
                } else {
                    val created = Room
                        .databaseBuilder(
                            context.applicationContext,
                            TouchLabDatabase::class.java,
                            NAME,
                        )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                    instance = created
                    created
                }
            }
        }
    }
}
