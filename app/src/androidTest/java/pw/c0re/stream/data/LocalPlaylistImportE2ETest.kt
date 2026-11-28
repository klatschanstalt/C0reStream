package pw.c0re.stream.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.ui.playlists.PlaylistsViewModel
import pw.c0re.stream.ui.playlists.decodeQrBitmap
import pw.c0re.stream.ui.playlists.localizedImportError
import pw.c0re.stream.ui.playlists.playlistNameFromFileName
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * End-to-end coverage of UI callback -> SAF URI -> ContentResolver -> parser -> Room -> display
 * data. Uses real document URIs and the application database; no parser-only shortcut.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaylistImportE2ETest {

    private lateinit var context: Context
    private lateinit var app: C0reStreamApp
    private lateinit var vm: PlaylistsViewModel
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        app = context as C0reStreamApp
        vm = PlaylistsViewModel(app)
    }

    @After
    fun tearDown() = runBlocking {
        val repo = PlaylistRepository(app.database)
        app.database.playlistDao().allOnce().forEach { repo.deletePlaylist(it.id) }
        createdFiles.forEach { file ->
            runCatching { file.setReadable(true) }
            runCatching { file.delete() }
        }
    }

    private fun document(content: String, name: String = "import-test.m3u"): Uri {
        val file = File(context.cacheDir, name).also { createdFiles += it }
        file.writeText(content, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    /** Runs the real import on a controllable scope and returns the stored playlist id. */
    private fun importSync(uri: Uri?, maxBytes: Long = PlaylistsViewModel.MAX_IMPORT_BYTES): Long? {
        val done = CompletableDeferred<Long>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = vm.importFile(
            uri = uri,
            onDone = { done.complete(it) },
            scope = scope,
            maxBytes = maxBytes,
        )
        return runBlocking {
            withTimeout(20_000) { job?.join() }
            val result = if (done.isCompleted) done.getCompleted() else null
            scope.cancel()
            result
        }
    }

    /** Room DAOs are suspend; bridge them from the synchronous JUnit test methods. */
    private fun <T> db(block: suspend () -> T): T = runBlocking { block() }

    private fun channelText(count: Int): String = buildString {
        append("#EXTM3U\n")
        repeat(count) { index ->
            append("#EXTINF:-1 tvg-id=\"id-$index\" group-title=\"News\",Kanal $index\n")
            append("https://stream.example/$index.ts\n")
        }
    }

    @Test
    fun `valid m3u with special filename is stored with every channel and survives reopen`() {
        val uri = document(
            content = channelText(3),
            // Provider file names may contain accents, dashes and non-ASCII symbols.
            name = "Méditerranée — TEST №1.m3u",
        )
        val id = importSync(uri)
        assertNotNull("import must complete", id)
        assertNull("lastError", vm.lastError.value)
        val playlistId = id!!

        val playlist = db { app.database.playlistDao().byId(playlistId) }
        assertNotNull(playlist)
        assertEquals("Méditerranée — TEST №1", playlist!!.name)
        assertNull("local playlists have no network source", playlist.url)
        assertEquals(3, db { app.database.channelDao().countForPlaylist(playlistId) })

        val channels = db { app.database.channelDao().snapshot(playlistId) }
        assertEquals(listOf("Kanal 0", "Kanal 1", "Kanal 2"), channels.map { it.name })
        assertEquals(listOf(0, 1, 2), channels.map { it.orderIndex })
        assertEquals("https://stream.example/2.ts", channels[2].url)
        assertEquals("News", channels[0].group)

        // Simulate an app restart with a fresh database connection to the same on-disk file.
        val reopened = AppDatabase.build(context)
        try {
            assertEquals(3, db { reopened.playlistDao().byId(playlistId)?.channelCount })
            assertEquals(3, db { reopened.channelDao().countForPlaylist(playlistId) })
            assertEquals("Kanal 2", db { reopened.channelDao().snapshot(playlistId) }[2].name)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `valid m3u8 with utf8 bom and generic mime shape is accepted`() {
        val uri = document(
            content = "\uFEFF#EXTM3U\r\n#EXTINF:-1,Übertragung\r\nhttps://stream.example/live/index.m3u8\r\n",
            name = "playlist.m3u8",
        )
        val id = importSync(uri)
        assertNotNull("BOM/CRLF m3u8 must import", id)
        assertNull(vm.lastError.value)
        val channel = db { app.database.channelDao().snapshot(id!!) }.single()
        assertEquals("Übertragung", channel.name)
        assertEquals("https://stream.example/live/index.m3u8", channel.url)
    }

    @Test
    fun `empty corrupt and url-less files are refused without storing a playlist`() {
        val empty = document("", "empty.m3u")
        val binary = File(context.cacheDir, "corrupt.m3u").also { createdFiles += it }
        binary.writeBytes(
            byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 0, 0, 0, 0x0d.toByte()),
        )
        val noUrl = document("#EXTM3U\n# just a header\n", "header.m3u")

        listOf(empty, Uri.fromFile(binary), noUrl).forEach { uri ->
            assertNull(importSync(uri))
            assertEquals(
                context.getString(R.string.error_import_format),
                vm.lastError.value,
            )
        }
        assertTrue(db { app.database.playlistDao().allOnce() }.isEmpty())
    }

    @Test
    fun `oversized file is rejected without partial storage`() {
        val uri = document(channelText(40), "large.m3u")
        assertNull(importSync(uri, maxBytes = 128L))
        assertEquals(context.getString(R.string.error_import_too_large), vm.lastError.value)
        assertTrue(db { app.database.playlistDao().allOnce() }.isEmpty())
    }

    @Test
    fun `picker cancellation is a no op`() {
        assertNull(vm.importFile(null, {}))
        assertNull(vm.lastError.value)
        assertNull(vm.lastImport.value)
        assertTrue(db { app.database.playlistDao().allOnce() }.isEmpty())
    }

    @Test
    fun `cancelling during import stores nothing and keeps no error state`() {
        val uri = document(channelText(5), "cancel.m3u")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).apply { cancel() }
        val job = vm.importFile(uri, {}, scope = scope)
        runBlocking { withTimeout(20_000) { job?.join() } }
        assertTrue(db { app.database.playlistDao().allOnce() }.isEmpty())
        assertNull(vm.lastError.value)
    }

    @Test
    fun `unreadable document is reported and stores nothing`() {
        val file = File(context.cacheDir, "locked.m3u").also { createdFiles += it }
        file.writeText(channelText(1))
        assertTrue(file.setReadable(false))

        assertNull(importSync(Uri.fromFile(file)))
        val allowed = setOf(
            context.getString(R.string.error_import_permission),
            context.getString(R.string.error_import_unreadable),
        )
        assertTrue("error=${vm.lastError.value}", vm.lastError.value in allowed)
        assertTrue(db { app.database.playlistDao().allOnce() }.isEmpty())
    }

    @Test
    fun `permission failure maps to localized non leaking message`() {
        assertEquals(
            context.getString(R.string.error_import_permission),
            localizedImportError(SecurityException(), context),
        )
    }

    @Test
    fun `derived names strip paths control characters and compression suffixes`() {
        assertEquals("passwd", playlistNameFromFileName("../../etc/passwd.txt", "fallback"))
        assertEquals("file", playlistNameFromFileName("file.m3u.gz", "fallback"))
        assertEquals("Kanal 1", playlistNameFromFileName("Kanal\u00091.m3u8", "fallback"))
        assertEquals("fallback", playlistNameFromFileName(".m3u", "fallback"))
        assertFalse(playlistNameFromFileName(null, "fallback").contains('/'))
    }

    private fun handlesSend(mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, "https://stream.example/playlist.m3u8")
        }
        val manager = context.packageManager
        return manager.queryIntentActivities(intent, 0).any { it.activityInfo.packageName == context.packageName }
    }

    @Test
    fun `share sheet registers only supported playlist text types`() {
        assertTrue("text/plain share must resolve to this app", handlesSend("text/plain"))
        assertTrue("mpegurl share must resolve to this app", handlesSend("application/x-mpegurl"))
        assertFalse("broad media types must not be registered", handlesSend("video/*"))
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        val chunk = ByteBuffer.allocate(12 + data.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(data.size)
            .put(typeBytes)
            .put(data)
            .putInt(crc.value.toInt())
        out.write(chunk.array())
    }

    /** Tiny PNG (header + IEND only) that declares 20 000 x 20 000 pixels. */
    private fun craftedHugePng(): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 0x0d, 0x0a, 0x1a, 0x0a))
        val ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(20_000)
            .putInt(20_000)
            .put(8.toByte()) // bit depth
            .put(2.toByte()) // truecolour
            .put(0.toByte()) // compression
            .put(0.toByte()) // filter
            .put(0.toByte()) // interlace
        writePngChunk(this, "IHDR", ihdr.array())
        writePngChunk(this, "IEND", ByteArray(0))
    }.toByteArray()

    @Test
    fun `highly compressed image with huge declared dimensions is rejected before full decode`() = runBlocking {
        val file = File(context.cacheDir, "huge-qr.png").also { createdFiles += it }
        val bytes = craftedHugePng()
        assertTrue("crafted image must stay small", bytes.size < 1_024)
        file.writeBytes(bytes)
        assertNull(decodeQrBitmap(context, Uri.fromFile(file)))
    }
}
