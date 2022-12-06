package com.brentvatne.exoplayer

import android.net.Uri

fun Uri?.fileName(): String? = this?.pathSegments?.last()

fun Uri?.fileNameWithoutExtension(): String? = this?.pathSegments?.last()?.split(".")?.first()

inline val Uri?.isLocalUrl : Boolean
   get() = this?.run { scheme == null || scheme == "file" } ?: false