package com.henriquesebastiao.downtify.core.model

/** A server-owned render, never a library track or an arbitrary media URL. */
data class DjReference(val id: String, val mode: String)

object DjIds {
    private val identity = Regex("[a-f0-9]{32}")
    fun valid(id: String) = identity.matches(id)
    fun mediaId(id: String, mode: String = "prepared"): String {
        require(valid(id) && mode in setOf("prepared", "interactive"))
        return "dj:$mode:$id"
    }
    fun referenceOf(mediaId: String?): DjReference? {
        val match = Regex("dj:(prepared|interactive):([a-f0-9]{32})").matchEntire(mediaId.orEmpty()) ?: return null
        return DjReference(match.groupValues[2], match.groupValues[1])
    }
    fun idOf(mediaId: String?): String? = referenceOf(mediaId)?.id
    fun isDj(mediaId: String?) = mediaId?.startsWith("dj:") == true
}
