package com.brentvatne.exoplayer

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import com.facebook.react.bridge.ReactContext
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter

object PlayerInstanceHolder {

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private var simpleExoPlayer: SimpleExoPlayer? = null
    private var mutableTrackSelector: DefaultTrackSelector? = null
    private var currentMediaItemsList: MutableList<MediaBrowserCompat.MediaItem>? = null
    val bandwidthMeter = DefaultBandwidthMeter()
    private var mutableMediaSourceList: List<MediaSource>? = null

    val trackSelector: DefaultTrackSelector?
        get() = mutableTrackSelector

    val mediaSourceList: ConcatenatingMediaSource
        get() = ConcatenatingMediaSource(*mutableMediaSourceList?.toTypedArray() ?: emptyArray())

    fun getPlayer(context: Context): SimpleExoPlayer {
        if (simpleExoPlayer == null) {
            mutableTrackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory()).apply {
                setParameters(buildUponParameters().setMaxVideoBitrate(Int.MAX_VALUE))
            }

            simpleExoPlayer =
                ExoPlayerFactory.newSimpleInstance(
                    context,
                    DefaultRenderersFactory(context),
                    mutableTrackSelector,
                    DefaultLoadControl(),
                    null,
                    bandwidthMeter
                ).apply {
                    setAudioAttributes(playerAudioAttributes, true)
                }
        }

        return simpleExoPlayer!!
    }

    fun stopPlayer(reset: Boolean = false) {
        simpleExoPlayer?.stop(reset)
    }

    fun updateCurrentMediaItemsList(items: MutableList<MediaBrowserCompat.MediaItem>?) {
        this.currentMediaItemsList = items
    }

    fun mapToCurrentWindowIndex(uri: Uri): Int {
        return currentMediaItemsList?.run {
            indexOf(
                find { it.description.mediaUri.toString() == uri.toString() }
            )
        } ?: -1
    }

    fun convertToExoplayerDataSource(context: ReactContext) {
        val mediaDataSourceFactory =
            DataSourceUtil.getDefaultDataSourceFactory(context, bandwidthMeter, null)
        mutableMediaSourceList = currentMediaItemsList?.map { mediaItem ->
            this.buildMediaSource(
                mediaDataSourceFactory,
                mediaItem.description.mediaUri
            )
        }
    }

    private fun buildMediaSource(mediaDataSourceFactory: DataSource.Factory, uri: Uri?) =
        ExtractorMediaSource.Factory(mediaDataSourceFactory)
            .setExtractorsFactory(DefaultExtractorsFactory())
            .createMediaSource(uri)

    fun releasePlayer() {
        stopPlayer(true)
        simpleExoPlayer?.release()
        simpleExoPlayer = null
        mutableTrackSelector = null
    }
}