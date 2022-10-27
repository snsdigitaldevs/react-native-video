package com.brentvatne.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.brentvatne.react.R
import com.brentvatne.media.data.DataSource
import com.brentvatne.media.play.PlayerConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val BROWSABLE_ROOT = "/"
private const val EMPTY_ROOT = "@Empty@"

class MediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var packageValidator: PackageValidator
    private var currentStatus = Status.NOT_START
    private lateinit var errorIcon: Bitmap
    private val dataSource = DataSource()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, javaClass.simpleName)
        sessionToken = mediaSession.sessionToken
        packageValidator = PackageValidator(this)
        errorIcon = BitmapFactory.decodeResource(resources, R.drawable.pimsleuricon11)
    }

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?
    ): BrowserRoot {
        return if (packageValidator.isValidCaller(clientPackageName, clientUid)) {
            BrowserRoot(BROWSABLE_ROOT, null)
        } else {
            BrowserRoot(EMPTY_ROOT, null)
        }
    }

    override fun onLoadChildren(
        parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != BROWSABLE_ROOT) {
            result.sendResult(null)
            return
        }

        val errorMessage = when (currentStatus) {
            Status.NOT_START -> "Please start Pimsleur on your phone"
            Status.NOT_SIGN_IN -> "Please sign in Pimsleur on your phone"
            Status.CHOOSING_SUB_USER -> "Please choose a user"
            Status.CHOOSING_LIBRARY -> "Please choose a language"
            Status.WAITING_FOR_LOADING_PLAY_LIST -> "Loading...."
            Status.UNKNOWN -> "UnknownError"
            Status.LOADING_PLAY_LIST -> {
                loadPlayList(result)
                return
            }
        }
        result.sendResult(buildErrorMessageList(errorMessage))
    }

    private fun loadPlayList(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val isLoadSuccess = dataSource.whenReady {
            if (it?.isNotEmpty() == true) {
                PlayerConnector.bindMediaSession(mediaSession)
                PlayerConnector.bindCurrentPlayingList(it)
                result.sendResult(dataSource.mediaItemsList)
            } else {
                result.sendResult(null)
            }
        }
        if (!isLoadSuccess) result.detach()
    }

    private fun buildErrorMessageList(message: String): MutableList<MediaBrowserCompat.MediaItem> =
        mutableListOf<MediaBrowserCompat.MediaItem>().apply {
            val descriptionCompat = MediaDescriptionCompat.Builder()
                .setMediaId(message.hashCode().toString())
                .setTitle("Pimsleur is not available now")
                .setSubtitle(message)
                .setIconBitmap(errorIcon)
            add(MediaBrowserCompat.MediaItem(descriptionCompat.build(), FLAG_PLAYABLE))
        }

    override fun onCustomAction(action: String, extras: Bundle?, result: Result<Bundle>) {
        currentStatus = when (action) {
            ACTION_SIGN_IN -> Status.NOT_SIGN_IN
            ACTION_LOAD_PLAY_LIST -> parseProductData(extras)
            ACTION_CHOOSE_SUB_USER -> Status.CHOOSING_SUB_USER
            ACTION_CHOOSE_LIBRARY -> Status.CHOOSING_LIBRARY
            else -> Status.UNKNOWN
        }
        notifyChildrenChanged(BROWSABLE_ROOT)
        result.sendResult(Bundle.EMPTY)
    }

    private fun parseProductData(extras: Bundle?): Status {
        val actionStatus = extras?.getString(KEY_BUNDLE_ACTION_STATE, WAITING_FOR_EXECUTING)
        if (actionStatus != ACTION_EXECUTED_SUCCESS) return Status.WAITING_FOR_LOADING_PLAY_LIST
        parseProductInfo((extras.getString(KEY_BUNDLE_PRODUCT_INFO) ?: ""))
        return Status.LOADING_PLAY_LIST
    }

    private fun parseProductInfo(data: String) {
        serviceScope.launch {
            dataSource.buildMediaList(data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        serviceJob.cancel()
        if (!errorIcon.isRecycled) errorIcon.recycle()
    }
}

enum class Status { NOT_START, NOT_SIGN_IN, CHOOSING_SUB_USER, CHOOSING_LIBRARY, WAITING_FOR_LOADING_PLAY_LIST, LOADING_PLAY_LIST, UNKNOWN }
