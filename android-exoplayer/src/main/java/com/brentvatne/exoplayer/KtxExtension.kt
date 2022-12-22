package com.brentvatne.exoplayer

import android.net.Uri
import android.support.v4.media.MediaDescriptionCompat

fun Uri?.fileName(): String? = this?.pathSegments?.last()

fun Uri?.fileNameWithoutExtension(): String? =  this?.run { (lastPathSegment ?: toString()).split(".").first() }

inline val Uri?.isLocalUrl : Boolean
   get() = this?.run { scheme == null || scheme == "file" } ?: false

inline val MediaDescriptionCompat.alternativeAudioLink: String?
  get() = this.extras?.getString(DESCRIPTION_EXTRAS_KEY_ALTERNATIVE_AUDIO_LINK)