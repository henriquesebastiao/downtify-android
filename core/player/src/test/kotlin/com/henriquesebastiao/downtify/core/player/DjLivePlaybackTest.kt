package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.henriquesebastiao.downtify.core.network.ServerUrls
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjLivePlaybackTest {
    private val id = "a".repeat(32)

    @Test fun `a restored direct keeps HLS mode MIME and server relative identity`() {
        val item = DjMediaItems.from(id, "Synthetic live", 60000, "interactive")
        val restored = MediaItems.restored(MediaItems.saved(item), "https://fixture.test")
        assertEquals("interactive", DjMediaItems.of(restored)?.mode)
        assertEquals(MimeTypes.APPLICATION_M3U8, restored.localConfiguration?.mimeType)
        assertEquals(DjMediaItems.uri(id, "interactive"), restored.localConfiguration?.uri)
        assertNull(MediaItems.trackFromMetadata(restored))
    }

    @Test fun `HLS manifest and segment requests bypass cache only on the paired server`() {
        val base = "https://fixture.test/downtify"
        val resource = "$base/api/v1/music-ai/dj/sessions/$id/hls/segment-00000001.ts"
        assertEquals(true, ServerUrls.isDjHlsResource(base, resource))
        assertFalse(ServerUrls.isDjHlsResource(base, resource.replace("fixture.test", "other.test")))
        assertFalse(ServerUrls.isDjHlsResource(base, "$resource?credential=caller"))
        val cached = mockk<DataSource.Factory>()
        val direct = mockk<DataSource.Factory>()
        val upstream = mockk<DataSource>(relaxed = true)
        every { direct.createDataSource() } returns upstream
        every { upstream.open(any()) } returns 100L
        val source = DjDataSource(cached, direct) { ServerUrls.isDjHlsResource(base, it.toString()) }
        source.open(DataSpec.Builder().setUri(resource).build())
        source.close()
        verify(exactly = 0) { cached.createDataSource() }
        verify(exactly = 1) { direct.createDataSource() }
    }
}
