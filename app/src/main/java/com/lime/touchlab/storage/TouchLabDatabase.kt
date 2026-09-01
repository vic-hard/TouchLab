package com.lime.touchlab.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальное хранилище завершённых попыток.
 *
 * WAL включён по умолчанию: зафиксированная транзакция переживает убийство процесса,
 * Миграций нет и не планируется — на этапе 1
 * схема одна; если она изменится до сдачи, база пересоздаётся вручную переустановкой.
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
    version = 1,
    exportSchema = false,
)
abstract class TouchLabDatabase : RoomDatabase() {

    abstract fun dao(): TouchLabDao

    companion object {
        private const val NAME: String = "touchlab.db"

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
                        .build()
                    instance = created
                    created
                }
            }
        }
    }
}
