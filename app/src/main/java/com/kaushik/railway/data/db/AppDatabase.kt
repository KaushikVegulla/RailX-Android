package com.kaushik.railway.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookingEntity::class, WatchEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun watchDao(): WatchDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Old rows carry no trustworthy account identity. Preserve them as unowned.
                db.execSQL("ALTER TABLE bookings ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_ownerId_createdAt ON bookings (ownerId, createdAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Departure time and availability status at booking time
                // (needed for IRCTC refund quotes), plus the applied
                // cancellation charge and refund on cancelled bookings.
                db.execSQL("ALTER TABLE bookings ADD COLUMN departTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN classStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN cancelCharge INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN refundAmount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Train Watch: one row per watched train/date/class.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS train_watches (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "ownerId TEXT NOT NULL, trainNumber TEXT NOT NULL, trainName TEXT NOT NULL, " +
                        "fromCode TEXT NOT NULL, toCode TEXT NOT NULL, date TEXT NOT NULL, " +
                        "classCode TEXT NOT NULL, lastStatus TEXT NOT NULL, " +
                        "lastCheckedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_train_watches_ownerId_createdAt ON train_watches (ownerId, createdAt)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "railone.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}
