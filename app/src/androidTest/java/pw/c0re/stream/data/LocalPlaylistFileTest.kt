package pw.c0re.stream.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalPlaylistFileTest {
    @Test
    fun localM3uFileIsReadAndParsed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "c0re-local-test.m3u").apply {
            writeText("\uFEFF#EXTM3U\r\n#EXTINF:-1,Test Channel\r\nhttps://example.test/channel.ts\r\n")
        }
        val uri = Uri.fromFile(file)
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input).readBytes()
        }
        val result = M3UParser.parseWithStats(bytes)
        assertTrue(result.stats.rejected == 0 || result.channels.isNotEmpty())
        assertEquals(1, result.channels.size)
        assertEquals("Test Channel", result.channels[0].name)
        assertEquals("https://example.test/channel.ts", result.channels[0].url)
    }
}
