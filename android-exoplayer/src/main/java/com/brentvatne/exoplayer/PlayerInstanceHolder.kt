package com.brentvatne.exoplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.util.Log
import com.facebook.react.bridge.ReactContext
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.AudioFocusGain
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter

object PlayerInstanceHolder {

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_SPEECH)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private var simpleExoPlayer: SimpleExoPlayer? = null
    private var mutableTrackSelector: DefaultTrackSelector? = null
    private var currentMediaItemsList: MutableList<MediaItem>? = null
    private var mutableBandwidthMeter: BandwidthMeter? = null
    private var mutableMediaSourceList: List<MediaSource>? = null
    private var mutableIsSwitchedToOtherSource = false
    private var mutableResumePosition: Long = C.TIME_UNSET

    val trackSelector: DefaultTrackSelector?
        get() = mutableTrackSelector

    val bandwidthMeter: DefaultBandwidthMeter?
        get() = mutableBandwidthMeter as DefaultBandwidthMeter?

    val mediaSourceList: ConcatenatingMediaSource
        get() = ConcatenatingMediaSource(*mutableMediaSourceList?.toTypedArray() ?: emptyArray())

    val isSwitchOtherSource: Boolean
        get() = mutableIsSwitchedToOtherSource

    val resumePosition: Long
        get() = mutableResumePosition

    val repeatMode: Int
        get() = simpleExoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF

    fun getPlayer(context: Context): SimpleExoPlayer {
        if (simpleExoPlayer == null) {
            mutableTrackSelector =
                DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()).apply {
                    setParameters(buildUponParameters().setMaxVideoBitrate(Int.MAX_VALUE))
                }

            mutableBandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

            simpleExoPlayer = SimpleExoPlayer.Builder(context)
                .setBandwidthMeter(mutableBandwidthMeter!!)
                .setTrackSelector(mutableTrackSelector!!)
                .build()
                .apply {
                    setAudioAttributes(playerAudioAttributes, true)
                }
        }
        return simpleExoPlayer!!
    }

    fun getCurrentMediaItem(currentWindowIndex: Int): MediaItem? {
        return currentMediaItemsList?.run {
            if (currentWindowIndex in 0 until size) return get(currentWindowIndex) else null
        }
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
        } ?: C.INDEX_UNSET
    }

    fun switchToOtherResource(isSwitched: Boolean) {
        mutableIsSwitchedToOtherSource = isSwitched
        mutableResumePosition =
            if (isSwitched) simpleExoPlayer?.currentPosition ?: C.TIME_UNSET else C.TIME_UNSET
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
        ProgressiveMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(uri)

    fun releasePlayer() {
        stopPlayer(true)
        simpleExoPlayer?.release()
        simpleExoPlayer = null
        mutableTrackSelector = null
    }
}