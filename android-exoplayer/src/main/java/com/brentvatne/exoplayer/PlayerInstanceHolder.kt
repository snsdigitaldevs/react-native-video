package com.brentvatne.exoplayer

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import com.facebook.react.bridge.ReactContext
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import java.io.File

const val DESCRIPTION_EXTRAS_KEY_ALTERNATIVE_AUDIO_LINK = "description_alternative_audio_link"
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

    val currentMediaDuration: Long
        get() = simpleExoPlayer?.duration ?: C.TIME_UNSET

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

    fun getMediaItem(currentWindowIndex: Int): MediaItem? {
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

    fun updateMediaItemToLocalUrl(uri: Uri): Boolean {
        if (!uri.isLocalUrl) return false
        val file = uri.path?.let { File(it) } ?: return false
        if (!file.exists()) return false

        val item = this.currentMediaItemsList?.find {
            it.description.mediaUri?.fileNameWithoutExtension() == uri.fileNameWithoutExtension()
        }
        return updateMediaItem(item, file)
    }

    fun updateMediaItemToLocalUrl(mediaId: String, file: File): Boolean {
        if (!file.exists()) return false
        val item = this.currentMediaItemsList?.find { it.mediaId == mediaId }
        return updateMediaItem(item, file)
    }

    private fun updateMediaItem(srcItem: MediaItem?, localMediaFile: File): Boolean {

        if (srcItem?.description?.mediaUri?.path == localMediaFile.absolutePath)  {
            return false
        }
        val localImageFile =
            localMediaFile.parent?.let { File(it, srcItem?.description?.iconUri?.fileName() ?: "") }

        val iconUrl =
            if (localImageFile?.exists() == true) Uri.parse(localImageFile.absolutePath) else srcItem?.description?.iconUri

        val localMediaItem = srcItem?.run {
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setMediaUri(Uri.fromFile(localMediaFile))
                .setTitle(description.title)
                .setSubtitle(description.subtitle)
                .setIconUri(iconUrl)
                .setExtras(description.extras)
                .build()
        }?.let { MediaItem(it, MediaItem.FLAG_PLAYABLE) }

        return this.currentMediaItemsList?.run {
            if (localMediaItem != null) {
                set(indexOf(srcItem), localMediaItem)
                true
            } else false
        } ?: false
    }

    fun mapToCurrentWindowIndex(uri: Uri): Int {
        return currentMediaItemsList?.run {
            indexOf(
                find {
                    it.description.mediaUri.toString() == uri.toString() ||
                    it.description.alternativeAudioLink == uri.toString()
                }
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