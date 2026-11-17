package com.henriquesebastiao.downtify.core.data.library

import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.RecentContextEntity
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.isBareSongs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the user played from lately, kept on the phone for Home's "Jump back in". */
@Singleton
class RecentsRepository @Inject constructor(private val db: DowntifyDatabase) {
    fun recents(limit: Int = HOME_LIMIT): Flow<List<RecentContext>> =
        db.recents().observe(limit).map { rows -> rows.mapNotNull { it.toModel() }.filterNot { it.isBareSongs } }

    suspend fun record(context: PlaybackContext, coverTrackId: String?, now: Long = System.currentTimeMillis()) {
        val entity = RecentContextEntity(
            key = "${context.type.name}:${context.refId}",
            type = context.type.name,
            refId = context.refId,
            title = context.title,
            coverTrackId = coverTrackId,
            playedAt = now,
        )
        db.recents().upsert(entity)
        db.recents().trim(KEEP)
    }

    private companion object {
        const val HOME_LIMIT = 6
        const val KEEP = 24
    }
}
