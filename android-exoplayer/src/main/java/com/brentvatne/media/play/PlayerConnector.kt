package com.brentvatne.media.play

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.IllegalSeekPositionException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource

object PlayerConnector {

    private var currentPlayingList: List<MediaBrowserCompat.MediaItem>? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentMediaSourceList: MutableList<MediaSource>? = null

    fun bindMediaSession(mediaSession: MediaSessionCompat) {
        this.mediaSession = mediaSession
    }

    fun bindCurrentPlayingList(currentPlayingList: List<MediaBrowserCompat.MediaItem>) {
        this.currentPlayingList = currentPlayingList
    }

    fun connectPlayer(player: SimpleExoPlayer) {
        if (mediaSession == null || currentPlayingList == null) return
        MediaSessionConnector(mediaSession).apply {
            setPlayer(
                player,
                PlayerPlaybackPrepare(player),
                SkipBackwardTenActionProvider(player),
                SkipForwardTenActionProvider(player)
            )
            setQueueNavigator(PlayerQueueNavigator(mediaSession))
        }
    }

    fun buildMediaSourceList(performAction: (uri: Uri, extension: String) -> MediaSource): List<MediaSource> {
        return mutableListOf<MediaSource>().apply {
            currentPlayingList?.forEach { mediaItem ->
                mediaItem.description.mediaUri?.let { uri ->
                    add(performAction(uri, ""))
                }
            }
            currentMediaSourceList = this
        }
    }

    fun mapCurrentWindowIndex(uri: Uri): Int {
        return currentPlayingList?.run {
            indexOf(
                find { it.description.mediaUri.toString() == uri.toString() }
            )
        } ?: -1
    }

    private class PlayerQueueNavigator(mediaSession: MediaSessionCompat) :
        TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(
            player: Player?,
            windowIndex: Int
        ): MediaDescriptionCompat {
            return currentPlayingList?.run {
                if (windowIndex < size) {
                    get(windowIndex).description
                } else null
            } ?: MediaDescriptionCompat.Builder().build()
        }
    }

    private class PlayerPlaybackPrepare(private val player: SimpleExoPlayer) :
        MediaSessionConnector.PlaybackPreparer {
        override fun getCommands(): Array<String> = emptyArray()

        override fun onCommand(
            player: Player?,
            command: String?,
            extras: Bundle?,
            cb: ResultReceiver?
        ) = Unit

        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        override fun onPrepare() {
            // do nothing
        }


        override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
            currentMediaSourceList?.let {
                player.prepare(ConcatenatingMediaSource(*it.toTypedArray()))
                val itemToPlay = currentPlayingList?.find { item -> item.mediaId == mediaId }
                val currentWindowIndex = currentPlayingList?.indexOf(itemToPlay) ?: 0
                if (currentWindowIndex >= 0 && player.isCurrentWindowSeekable) {
                    player.seekTo(currentWindowIndex, 0)
                }
            }
        }

        override fun onPrepareFromSearch(query: String?, extras: Bundle?) = Unit
        override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) = Unit
    }
}
