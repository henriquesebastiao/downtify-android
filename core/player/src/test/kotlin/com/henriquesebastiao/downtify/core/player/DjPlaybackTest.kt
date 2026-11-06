package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.henriquesebastiao.downtify.core.model.DjIds
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
class DjPlaybackTest {
    private val id = "a".repeat(32)

    @Test fun `a persisted DJ item restores its logical identity rather than a track URL`() {
        val item = DjMediaItems.from(id, "Synthetic mix", 60000)
        val restored = MediaItems.restored(MediaItems.saved(item), "https://fixture.test")
        assertEquals(id, DjMediaItems.of(restored)?.id)
        assertEquals(DjMediaItems.uri(id), restored.localConfiguration?.uri)
        assertNull(MediaItems.trackFromMetadata(restored))
        assertEquals(true, PlayerState(dj = PlayingDj(id, "Mix")).hasMedia)
    }

    @Test fun `DJ routing bypasses the track cache on the initial request and after seek`() {
        val cached = mockk<DataSource.Factory>()
        val direct = mockk<DataSource.Factory>()
        val upstream = mockk<DataSource>(relaxed = true)
        every { direct.createDataSource() } returns upstream
        every { upstream.open(any()) } returns 100L
        val source = DjDataSource(cached, direct)
        source.open(DataSpec.Builder().setUri(DjMediaItems.uri(id)).build())
        source.close()
        source.open(DataSpec.Builder().setUri(DjMediaItems.uri(id)).setPosition(64).build())
        source.close()
        verify(exactly = 0) { cached.createDataSource() }
        verify(exactly = 2) { direct.createDataSource() }
        verify(exactly = 2) { upstream.close() }
    }

    @Test fun `normal tracks keep the existing cache route`() {
        val cached = mockk<DataSource.Factory>()
        val direct = mockk<DataSource.Factory>()
        val upstream = mockk<DataSource>(relaxed = true)
        every { cached.createDataSource() } returns upstream
        every { upstream.open(any()) } returns 100L
        val source = DjDataSource(cached, direct)
        source.open(DataSpec.Builder().setUri(MediaItems.uriFor("track")).build())
        source.close()
        verify(exactly = 1) { cached.createDataSource() }
        verify(exactly = 0) { direct.createDataSource() }
    }

    @Test fun `a render cannot supply an arbitrary URL or path`() {
        assertFalse(DjIds.valid("../../private"))
        assertFalse(DjIds.valid("https://other.test/file"))
        assertNull(DjIds.idOf("dj:prepared:../secret"))
        assertNull(DjMediaItems.idOf(Uri.parse("downtify://dj/$id/other")))
        assertEquals(
            "https://fixture.test/api/v1/music-ai/dj/sessions/$id/media",
            ServerUrls.djFile("https://fixture.test/", id),
        )
    }
}
