package com.henriquesebastiao.downtify.core.model

/** Only native library ids may reach track statistics, favorites and transcoding. */
object MediaIds {
    fun isLibrary(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return when {
            EpisodeIds.isEpisode(id) -> false
            StreamIds.isStream(id) -> false
            DjIds.isDj(id) -> false
            else -> true
        }
    }
}
