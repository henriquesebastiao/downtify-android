package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cover of a DJ stream belongs to the track that is audible, not to the render id. Using the
 * render id asked the server for a cover that does not exist, so Android Auto showed none or a
 * stale one.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionArtworkCoverTest {
    private val render = "0123456789abcdef0123456789abcdef"

    private fun djItem(trackId: String?, artwork: Uri? = null): MediaItem {
        val base = DjMediaItems.from(render, "Mix DJ", 1_000L)
        val extras = Bundle().apply { trackId?.let { putString("djTrackId", it) } }
        val metadata = base.mediaMetadata.buildUpon().setExtras(extras)
        artwork?.let { metadata.setArtworkUri(it) }
        return base.buildUpon().setMediaMetadata(metadata.build()).build()
    }

    @Test fun `a dj item is covered by the track that is audible`() {
        val uri = coverSource(djItem("track-1"), "https://host")!!
        assertTrue("attendu le titre, obtenu $uri", uri.toString().contains("track-1"))
        assertFalse("le rendu DJ ne doit pas servir d'identifiant de pochette", uri.toString().contains(render))
    }

    @Test fun `a dj item without a known track keeps the cover of its mix`() {
        val mix = Uri.parse("https://host/covers/mix-cover")
        assertEquals(mix, coverSource(djItem(null, mix), "https://host"))
    }

    @Test fun `a library track is still covered by its own id`() {
        val item = MediaItem.Builder().setMediaId("library-track").setUri("https://host/audio").build()
        val uri = coverSource(item, "https://host")!!
        assertTrue(uri.toString().contains("library-track"))
    }
}
