package com.brentvatne.media.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.bitmaps.PlatformBitmapFactory
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.BasePostprocessor
import com.facebook.imagepipeline.request.ImageRequestBuilder
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch

class ImageContentProvider : ContentProvider() {
  companion object {

    private val uriMap = mutableMapOf<Uri, Uri>()

    fun mapUri(uri: Uri): Uri {
      val path = uri.encodedPath?.substring(1)?.replace("/", ":") ?: return Uri.EMPTY
      val contentUri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority("com.thoughtworks.pimsleur.unlimited.development")
        .path(path)
        .build()
      uriMap[contentUri] = uri
      return contentUri
    }
  }

  override fun onCreate() = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val context = this.context ?: return null
    val remoteUri = uriMap[uri] ?: throw FileNotFoundException(uri.path)
    val file = uri.path?.let { File(context.cacheDir, it) } ?: return null
    val latch = CountDownLatch(1)
    if (!file.exists()) {
      val imageRequest = ImageRequestBuilder.newBuilderWithSource(remoteUri)
        .setPostprocessor(ImageCropProcess())
        .build()
      val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, this)
      dataSource.subscribe(object : BaseBitmapDataSubscriber() {
        override fun onNewResultImpl(bitmap: Bitmap?) {
          saveBitmap(bitmap, file)
          latch.countDown()
        }

        override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>?) {
          latch.countDown()
        }

      }, CallerThreadExecutor.getInstance())
    }
    latch.await()
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  private fun saveBitmap(bitmap: Bitmap?, file: File) {
    var outputStream: FileOutputStream? = null
    try {
      outputStream = FileOutputStream(file)
      bitmap?.compress(getCompressFormat(file), 40, outputStream)
      outputStream.flush()
    } finally {
      outputStream?.close()
    }
  }

  private fun getCompressFormat(file: File): Bitmap.CompressFormat {
    return when (file.extension) {
      "png" -> Bitmap.CompressFormat.PNG
      else -> Bitmap.CompressFormat.JPEG
    }
  }

  override fun query(

    p0: Uri,
    p1: Array<out String>?,
    p2: String?,
    p3: Array<out String>?,
    p4: String?
  ): Cursor? = null

  override fun getType(p0: Uri): String? = null

  override fun insert(p0: Uri, p1: ContentValues?): Uri? = null
  override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int = 0

  override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0
}

class ImageCropProcess : BasePostprocessor() {

  override fun getName(): String {
    return this.javaClass.simpleName
  }

  override fun process(
    sourceBitmap: Bitmap?,
    bitmapFactory: PlatformBitmapFactory?
  ): CloseableReference<Bitmap> {
    if (sourceBitmap == null || bitmapFactory == null) return super.process(
      sourceBitmap,
      bitmapFactory
    )
    val srcWidth = sourceBitmap.width
    val srcHeight = sourceBitmap.height
    return if (srcWidth >= srcHeight) {
      bitmapFactory.createBitmap(
        sourceBitmap,
        srcWidth / 2 - srcHeight / 2,
        0,
        srcHeight,
        srcHeight
      )
    } else {
      bitmapFactory.createBitmap(
        sourceBitmap,
        0,
        srcHeight / 2 - srcWidth / 2,
        srcWidth,
        srcWidth
      )
    }
  }
}
