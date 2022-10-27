package com.brentvatne.media.data

import androidx.annotation.Keep
import com.brentvatne.media.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
import com.brentvatne.media.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED

@Keep
data class Image(
  val fullImageAddress: String,
  val thumbImageAddress: String?
)

@Keep
data class MediaProcess(
  val current: Long,
  val total: Long
)

@Keep
data class MediaProgress(
  val status: String,
  val process: MediaProcess
) {
  fun getStatus(): Int {
    return if (status == "new") DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
    else DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
  }

  fun getPercent(): Double = (process.current * 1.0 / process.total).coerceAtMost(1.0)
}

@Keep
data class Lessons(
  val mediaItemId: Int,
  val name: String,
  val image: Image,
  val audioLink: String?,
  val level: Int,
  val lessonNumber: Int,
  val progress: MediaProgress
)

@Keep
data class ProductInfo(
  val languageName: String,
  val level: String,
  val lessons: List<Lessons>?,
  val isFree: Boolean
)

