package pw.c0re.stream.ui.profiles

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.profiles.PinFailure
import pw.c0re.stream.data.profiles.PinManager
import pw.c0re.stream.data.profiles.PinOperationResult
import pw.c0re.stream.data.profiles.PinSession
import pw.c0re.stream.data.profiles.PinVerification
import pw.c0re.stream.data.profiles.ProfileEntity
import pw.c0re.stream.data.profiles.ProfilePinOperations
import pw.c0re.stream.data.profiles.RoomProfilePinStore

/** Real DAO round-trip: authorization is decided from the stored entity, not a UI snapshot. */
@RunWith(AndroidJUnit4::class)
class ProfilePinOperationsInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `stored pin is verified before a change and before removal`() = runTest {
        val pins = PinManager()
        val dao = db.profileDao()
        val originalHash = pins.hash("1234".toCharArray())
        val id = dao.upsert(ProfileEntity(name = "Kids", pinHash = originalHash, createdAt = 1L))
        val operations = ProfilePinOperations(
            store = RoomProfilePinStore(dao),
            session = object : PinSession {
                override suspend fun activeId(): Long = id
                override suspend fun setActive(id: Long) = Unit
            },
            pins = pins,
        )

        assertEquals(
            PinOperationResult.Failure(PinFailure.WRONG_CURRENT),
            operations.changePin(id, "9999".toCharArray(), "5678".toCharArray(), "5678".toCharArray()),
        )
        assertEquals(originalHash, dao.byId(id)?.pinHash)

        assertEquals(
            PinOperationResult.Success,
            operations.changePin(id, "1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray()),
        )
        val changed = dao.byId(id)?.pinHash
        assertEquals(PinVerification.Success, pins.verify(changed, "5678".toCharArray(), id))

        assertEquals(
            PinOperationResult.Failure(PinFailure.WRONG_CURRENT),
            operations.removePin(id, "1234".toCharArray()),
        )
        assertEquals(changed, dao.byId(id)?.pinHash)
        assertEquals(PinOperationResult.Success, operations.removePin(id, "5678".toCharArray()))
        assertEquals(null, dao.byId(id)?.pinHash)
    }
}
