package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.PodcastEpisode
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.ServerUrls

/** Building Media3 items for library tracks. */
object MediaItems {
    /** Resolved to the real stream URL (and quality) only when the player opens it: see [StreamResolver]. */
    const val SCHEME = "downtify"
    private const val HOST = "track"
    private const val STREAM_HOST = "stream"
    private const val MS_PER_SECOND = 1000L
    private const val EXTRA_EPISODE_ID = "downtify.episode_id"
    private const val EXTRA_SHOW_ID = "downtify.show_id"
    private const val EXTRA_FILE = "downtify.file"
    private const val EXTRA_VIDEO_ID = "downtify.video_id"
    private const val EXTRA_RAW = "downtify.raw_song"
    private const val EXTRA_CONTEXT_TYPE = "downtify.context_type"
    private const val EXTRA_CONTEXT_ID = "downtify.context_id"

    fun streamUri(videoId: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(STREAM_HOST).appendPath(videoId).build()

    /** The video id when [uri] is a stream address, else null. */
    fun streamVideoIdOf(uri: Uri): String? = if (uri.scheme == SCHEME && uri.authority == STREAM_HOST) {
        uri.lastPathSegment?.takeIf { StreamVideo.isVideoId(it) }
    } else {
        null
    }

    fun uriFor(trackId: String): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(trackId).build()

    fun trackIdOf(uri: Uri): String? = if (uri.scheme == SCHEME && uri.authority == HOST) uri.lastPathSegment else null

    fun from(track: Track, baseUrl: String?): MediaItem = MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uriFor(track.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.displayTitle)
                .setArtist(track.displayArtist)
                .setAlbumTitle(track.album)
                .setAlbumArtist(track.albumArtist)
                .setTrackNumber(track.trackNumber.takeIf { it > 0 })
                .setDurationMs((track.duration * 1000).toLong())
                .setArtworkUri(
                    if (track.hasCover && baseUrl != null) {
                        Uri.parse(ServerUrls.cover(baseUrl, track.id, ServerUrls.COVER_LARGE))
                    } else {
                        null
                    },
                )
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    /**
     * A downloaded podcast episode, played from the server's file (`/downloads/...`). The address also
     * goes in the request metadata: controllers' items lose their URI on the way to the service.
     * Null while the server doesn't have the file yet.
     */
    fun episode(episode: PodcastEpisode, show: PodcastShow, baseUrl: String): MediaItem? {
        val file = episode.filename ?: return null
        val url = Uri.parse(EpisodeIds.fileUrl(baseUrl, file))
        return MediaItem.Builder()
            .setMediaId(EpisodeIds.mediaId(episode.id))
            .setUri(url)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(url).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(show.name)
                    .setAlbumTitle(show.name)
                    .setDurationMs(episode.durationSeconds * MS_PER_SECOND)
                    .setArtworkUri(show.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setExtras(
                        Bundle().apply {
                            putLong(EXTRA_EPISODE_ID, episode.id)
                            putLong(EXTRA_SHOW_ID, show.id)
                            putString(EXTRA_FILE, file)
                        },
                    )
                    .build(),
            )
            .build()
    }

    /**
     * A song that isn't in the library, played in full from the server's
     * stream cache. The address resolves when the player opens it (see
     * [StreamResolver]); the extras carry everything the UI needs without
     * a library row (download via [RemoteSong.raw], prefetch, display).
     */
    fun stream(song: RemoteSong, videoId: String): MediaItem {
        val url = streamUri(videoId)
        return MediaItem.Builder()
            .setMediaId(StreamIds.mediaId(videoId))
            .setUri(url)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(url).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setDurationMs((song.durationSeconds * MS_PER_SECOND).toLong())
                    .setArtworkUri(song.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(
                        Bundle().apply {
                            putString(EXTRA_VIDEO_ID, videoId)
                            putString(EXTRA_RAW, song.raw)
                        },
                    )
                    .build(),
            )
            .build()
    }

    /** The stream [item] stands for, or null for library and episode items. */
    fun streamOf(item: MediaItem): PlayingStream? {
        val videoId = StreamIds.videoIdOf(item.mediaId) ?: return null
        val extras = item.mediaMetadata.extras
        return PlayingStream(
            videoId = videoId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            coverUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
            raw = extras?.getString(EXTRA_RAW).orEmpty(),
        )
    }

    /** The episode [item] stands for, or null for a song. */
    fun episodeOf(item: MediaItem): PlayingEpisode? {
        if (!EpisodeIds.isEpisode(item.mediaId)) return null
        val extras = item.mediaMetadata.extras
        return PlayingEpisode(
            episodeId = EpisodeIds.episodeIdOf(item.mediaId) ?: return null,
            showId = extras?.getLong(EXTRA_SHOW_ID) ?: 0,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            showName = item.mediaMetadata.artist?.toString().orEmpty(),
            artworkUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
            file = extras?.getString(EXTRA_FILE).orEmpty(),
        )
    }

    fun contextMetadata(context: PlaybackContext): MediaMetadata = MediaMetadata.Builder()
        .setTitle(context.title)
        .setExtras(
            Bundle().apply {
                putString(EXTRA_CONTEXT_TYPE, context.type.name)
                putString(EXTRA_CONTEXT_ID, context.refId)
            },
        )
        .build()

    fun contextOf(metadata: MediaMetadata): PlaybackContext? {
        val extras = metadata.extras ?: return null
        val type =
            PlaybackContextType.entries.firstOrNull { it.name == extras.getString(EXTRA_CONTEXT_TYPE) } ?: return null
        return PlaybackContext(type, extras.getString(EXTRA_CONTEXT_ID).orEmpty(), metadata.title?.toString().orEmpty())
    }
}
