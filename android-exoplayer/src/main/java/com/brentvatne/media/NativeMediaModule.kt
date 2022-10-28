package com.brentvatne.media

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.ConnectionCallback
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

const val ACTION_NAMESPACE = "com.thoughtworks.pimsleur."
const val ACTION_SIGN_IN = "${ACTION_NAMESPACE}ACTION_SIGN_IN"
const val ACTION_LOAD_PLAY_LIST = "${ACTION_NAMESPACE}ACTION_LOAD_PLAY_LIST"
const val ACTION_CHOOSE_SUB_USER = "${ACTION_NAMESPACE}ACTION_CHOOSE_SUB_USER"
const val ACTION_CHOOSE_LIBRARY = "${ACTION_NAMESPACE}ACTION_CHOOSE_LIBRARY"

const val KEY_BUNDLE_ACTION_STATE = "bundle_action_state"
const val KEY_BUNDLE_PRODUCT_INFO = "bundle_product_info"

const val ACTION_EXECUTED_FAILED = "ACTION_EXECUTED_FAILED"
const val WAITING_FOR_EXECUTING = "ACTION_WAITING_FOR_EXECUTING"
const val ACTION_EXECUTED_SUCCESS = "ACTION_EXECUTED_SUCCESS"

private const val TIME_OUT = 3000L

class NativeMediaModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    private var mediaController: MediaControllerCompat? = null
    private val mediaServiceConnectionCallback = MediaServiceConnectionCallback(reactContext)
    private var mediaBrowser: MediaBrowserCompat? = null

    init {
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    /**
     * Send action from react-native to native
     *
     */
    @ReactMethod
    fun sendAction(
        action: String,
        actionState: String,
        params: ReadableMap? = null
    ) {
        val startTime = System.currentTimeMillis()
        while (true) {
            if (System.currentTimeMillis() - startTime > TIME_OUT) break
            if (mediaBrowser?.isConnected == true) {
                mediaBrowser?.sendCustomAction(
                    ACTION_NAMESPACE.plus(action),
                    Bundle().apply {
                        putString(KEY_BUNDLE_ACTION_STATE, actionState)
                        putString(
                            KEY_BUNDLE_PRODUCT_INFO,
                            params?.toHashMap()?.get("data").toString()
                        )
                    },
                    null
                )
                break
            }
        }
    }

    private fun emitEvent(eventName: String, params: WritableMap) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            val params = Arguments.createMap()
            val mediaId = metadata?.description?.mediaId
            params.putString("mediaId", mediaId)
            emitEvent("onMediaChange", params)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
        }
    }

    private inner class MediaServiceConnectionCallback(private val context: ReactApplicationContext) :
        ConnectionCallback() {
        override fun onConnected() {
            super.onConnected()

            mediaController = mediaBrowser?.sessionToken?.let {
                MediaControllerCompat(
                    context,
                    it
                ).apply { registerCallback(controllerCallback) }
            }

            mediaBrowser?.root?.let { mediaBrowser?.subscribe(it, subscriptionCallback) }

        }
    }

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(
            parentId: String,
            children: MutableList<MediaBrowserCompat.MediaItem>
        ) {
            val mediaId = if (children.size > 0) children[0].mediaId else null
            mediaController?.transportControls?.prepareFromMediaId(mediaId, Bundle.EMPTY)
        }
    }

    private fun initMediaBrowser() {
        if (mediaBrowser == null) {
            mediaBrowser = MediaBrowserCompat(
                currentActivity,
                ComponentName(reactContext, MediaService::class.java),
                mediaServiceConnectionCallback,
                null
            )
        }

        mediaBrowser?.apply {
            if (!isConnected) {
                connect()
            }
        }
    }

    override fun onHostResume() {
        initMediaBrowser()
    }

    override fun onHostPause() {
        // Not implement
    }

    override fun onHostDestroy() {
        mediaBrowser?.disconnect()
        mediaBrowser = null
    }
}
