package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.TrashState
import com.henriquesebastiao.downtify.core.network.dto.TrashCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.TrashItemDto
import com.henriquesebastiao.downtify.core.network.dto.TrashListingDto
import com.henriquesebastiao.downtify.core.network.dto.TrashPreviewStatusDto
import com.henriquesebastiao.downtify.core.network.dto.TrashSelectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashContractTest {
    @Test
    fun `a missing or switched off trash never offers managing it`() {
        assertFalse(NetworkJson.decodeFromString<TrashCapabilitiesDto>("{}").toModel().canManage)
        assertFalse(
            NetworkJson.decodeFromString<TrashCapabilitiesDto>("""{"enabled":false,"can_manage":true}""")
                .toModel().canManage,
        )
        val on = NetworkJson.decodeFromString<TrashCapabilitiesDto>(
            """{"enabled":true,"can_manage":true,"retention_days":30}""",
        ).toModel()
        assertTrue(on.canManage)
        assertEquals(30, on.retentionDays)
    }

    @Test
    fun `a ready check keeps each file's path, format and title`() {
        val status = NetworkJson.decodeFromString<TrashPreviewStatusDto>(
            """
            {"state":"ready","preview":{"confirmation":"one-use-token","expires_at":123,"items":[
            {"id":"i1","file":"Artist/Song.flac","track_id":"t1",
            "metadata":{"title":"Song","artist":"Artist","codec":"flac","album":"Album","duration":215.4},
            "state":"preview","size":12345,"expires_at":null,"error":""}]}}
            """.trimIndent(),
        )
        val preview = status.preview!!.toModel()
        assertEquals("one-use-token", preview.confirmation)
        val item = preview.items.single()
        assertEquals("Artist/Song.flac", item.file)
        assertEquals("t1", item.trackId)
        assertEquals("FLAC", item.format)
        assertEquals("Song", item.displayTitle)
        assertNull(item.expiresAt)
        assertEquals(TrashState.Unknown, item.state)
    }

    @Test
    fun `selection sends library ids rather than paths`() {
        val encoded = NetworkJson.encodeToString(TrashSelectionDto.serializer(), TrashSelectionDto(listOf("t1", "t2")))
        assertEquals("""{"track_ids":["t1","t2"]}""", encoded)
    }

    @Test
    fun `unknown states, null fields and fractional times decode`() {
        val item = NetworkJson.decodeFromString<TrashItemDto>(
            """{"id":"i","state":"future-state","expires_at":123.5,"error":null,"metadata":{"title":null}}""",
        ).toModel()
        assertEquals(TrashState.Unknown, item.state)
        assertEquals(123_500L, item.expiresAt)
        assertEquals("", item.error)
        assertEquals("", item.title)
    }

    @Test
    fun `a listing page carries the totals of every match`() {
        val page = NetworkJson.decodeFromString<TrashListingDto>(
            """{"items":[{"id":"a","file":"a.mp3","state":"trashed","expires_at":1764288000.0}],"total":3,"size":99}""",
        ).toModel()
        assertEquals(3, page.total)
        assertEquals(99L, page.bytes)
        assertEquals(TrashState.Trashed, page.items.single().state)
        assertEquals(1_764_288_000_000L, page.items.single().expiresAt)
    }
}
