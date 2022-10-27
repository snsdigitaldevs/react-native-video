package com.brentvatne.media.data

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.brentvatne.media.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE
import com.brentvatne.media.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataSource {

  private val gson by lazy { Gson() }

  var mediaItemsList: MutableList<MediaBrowserCompat.MediaItem>? = null

  private val onReadyListener =
    mutableListOf<(MutableList<MediaBrowserCompat.MediaItem>?) -> Unit>()

  fun whenReady(performAction: (mediaItems: MutableList<MediaBrowserCompat.MediaItem>?) -> Unit): Boolean =
    if (mediaItemsList == null) {
      onReadyListener += performAction
      false
    } else {
      performAction(mediaItemsList)
      true
    }

  suspend fun buildMediaList(data: String) {
    mediaItemsList = null
    withContext(Dispatchers.IO) {
      var mediaItems: MutableList<MediaBrowserCompat.MediaItem>?
      try {
        val productInfo = gson.fromJson(data, ProductInfo::class.java)
        mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>().apply {
          val availableLessons = productInfo.lessons?.run {
            if (productInfo.isFree && isNotEmpty()) subList(0, 1)
            else this
          }
          availableLessons?.forEach { lesson ->
            if (lesson.audioLink != null) {
              val imageUri = lesson.image.run {
                ImageContentProvider.mapUri(
                  Uri.parse(thumbImageAddress ?: fullImageAddress)
                )
              }

              val progress = lesson.progress
              val extra = Bundle().apply {
                putInt(DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS, progress.getStatus())
                putDouble(DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, progress.getPercent())
              }

              val mediaMetadataDescription = MediaDescriptionCompat.Builder()
                .setMediaId(lesson.mediaItemId.toString())
                .setTitle(lesson.name)
                .setSubtitle(productInfo.languageName.plus(" Level ${productInfo.level} "))
                .setIconUri(imageUri)
                .setMediaUri(Uri.parse(lesson.audioLink))
                .setExtras(extra)
                .build()

              add(
                MediaBrowserCompat.MediaItem(
                  mediaMetadataDescription,
                  MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
              )
            }
          }

          if (productInfo.isFree) {
            add(buildOtherReminderMessageItem("Subscribe on your mobile device for access to all lessons."))
          }

        }
      } catch (_: JsonSyntaxException) {
        mediaItems = mutableListOf()
      }

      withContext(Dispatchers.Main) {
        mediaItemsList = mediaItems
        onReadyListener.forEach { performAction -> performAction(mediaItemsList) }
        onReadyListener.clear()
      }
    }
  }

  private fun buildOtherReminderMessageItem(message: String): MediaBrowserCompat.MediaItem {
    val descriptionCompat = MediaDescriptionCompat.Builder()
      .setMediaId(message.hashCode().toString())
      .setTitle(message)
    return MediaBrowserCompat.MediaItem(
      descriptionCompat.build(),
      MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )
  }
}
