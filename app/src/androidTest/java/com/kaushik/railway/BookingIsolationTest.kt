package com.kaushik.railway

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaushik.railway.data.db.AppDatabase
import com.kaushik.railway.data.db.BookingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the real v1 upgrade, Room schema validation, and account-restricted DAO queries. */
@RunWith(AndroidJUnit4::class)
class BookingIsolationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var name: String

    @Before fun openLegacyDatabase() {
        context = ApplicationProvider.getApplicationContext()
        name = "booking-isolation-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""
                CREATE TABLE bookings (
                    pnr TEXT NOT NULL PRIMARY KEY,
                    trainNumber TEXT NOT NULL, trainName TEXT NOT NULL,
                    fromCode TEXT NOT NULL, toCode TEXT NOT NULL,
                    fromName TEXT NOT NULL, toName TEXT NOT NULL,
                    travelClassCode TEXT NOT NULL, travelClassName TEXT NOT NULL,
                    fare INTEGER NOT NULL, date TEXT NOT NULL, quota TEXT NOT NULL,
                    passengersJson TEXT NOT NULL, contact TEXT NOT NULL, status TEXT NOT NULL,
                    amount INTEGER NOT NULL, paymentId TEXT NOT NULL, orderId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            old.execSQL("""
                INSERT INTO bookings VALUES (
                    'legacy', '12345', 'Legacy train', 'NDLS', 'MMCT', 'Delhi', 'Mumbai',
                    'SL', 'Sleeper', 100, '14 Nov 2026', 'GN', '[]', '9000000000',
                    'PAID', 100, '', '', 1
                )
            """.trimIndent())
            old.version = 1
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @After fun closeDatabase() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationPreservesLegacyRecordsWithoutAssigningThemToAnAccount() = runBlocking {
        val dao = database.bookingDao()
        assertTrue(dao.getAllBookings("user:alice").first().isEmpty())
        assertTrue(dao.getAllBookings("").first().isEmpty())
        assertNull(dao.getByPnr("user:alice", "legacy"))
        database.openHelper.readableDatabase.query("SELECT ownerId, contact FROM bookings WHERE pnr = 'legacy'").use {
            assertTrue(it.moveToFirst())
            assertEquals("", it.getString(0))
            assertEquals("9000000000", it.getString(1))
        }
    }

    @Test fun accountsCannotReadCancelOrDeleteEachOthersBookings() = runBlocking {
        val dao = database.bookingDao()
        dao.insert(booking("user:alice", "alice-pnr"))
        dao.insert(booking("user:bob", "bob-pnr"))
        assertEquals(listOf("alice-pnr"), dao.getAllBookings("user:alice").first().map { it.pnr })
        assertNull(dao.getByPnr("user:bob", "alice-pnr"))
        dao.updateStatus("user:bob", "alice-pnr", "CANCELLED")
        dao.delete("user:bob", "alice-pnr")
        assertEquals("PAID", dao.getByPnr("user:alice", "alice-pnr")?.status)
        dao.updateStatus("user:alice", "alice-pnr", "CANCELLED")
        assertEquals("CANCELLED", dao.getByPnr("user:alice", "alice-pnr")?.status)
        assertTrue(dao.getAllBookings("").first().isEmpty())
    }

    @Test fun duplicatePnrCannotReplaceAnotherOwnersBooking() = runBlocking {
        val dao = database.bookingDao()
        dao.insert(booking("user:alice", "same-pnr"))
        val error = runCatching { dao.insert(booking("user:bob", "same-pnr")) }.exceptionOrNull()
        assertNotNull(error)
        assertEquals("user:alice", dao.getByPnr("user:alice", "same-pnr")?.ownerId)
        assertNull(dao.getByPnr("user:bob", "same-pnr"))
    }

    private fun booking(owner: String, pnr: String) = BookingEntity(
        pnr = pnr, ownerId = owner, trainNumber = "12345", trainName = "Test train",
        fromCode = "NDLS", toCode = "MMCT", fromName = "Delhi", toName = "Mumbai",
        travelClassCode = "SL", travelClassName = "Sleeper", fare = 100,
        date = "14 Nov 2026", quota = "GN", passengersJson = "[]", contact = "9000000000",
        status = "PAID", amount = 100
    )
}
