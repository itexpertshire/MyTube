package com.github.libretube.helpers

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.obj.DownloadItem
import com.github.libretube.parcelable.DownloadData
import com.github.libretube.services.DownloadService
import java.nio.file.Path

object DownloadHelper {
    const val VIDEO_DIR = "video"
    const val AUDIO_DIR = "audio"
    const val SUBTITLE_DIR = "subtitle"
    const val METADATA_DIR = "metadata"
    const val THUMBNAIL_DIR = "thumbnail"
    const val DOWNLOAD_CHUNK_SIZE = 8L * 1024
    const val DEFAULT_TIMEOUT = 60 * 1000
    const val DEFAULT_RETRY = 5

    private fun getOfflineStorageDir(context: Context): Path {
        val file = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            context.filesDir
        } else {
            try {
                context.getExternalFilesDir(null)!!
            } catch (e: Exception) {
                context.filesDir
            }
        }
        return file.toPath()
    }

    fun getDownloadDir(context: Context, path: String): Path {
        // TODO: Use createDirectories() when https://issuetracker.google.com/issues/279034662 is
        // fixed.
        return getOfflineStorageDir(context).resolve(path).apply {
            toFile().mkdirs()
        }
    }

    fun getMaxConcurrentDownloads(): Int {
        return PreferenceHelper.getString(
            PreferenceKeys.MAX_CONCURRENT_DOWNLOADS,
            "6",
        ).toFloat().toInt()
    }

    fun startDownloadService(context: Context, downloadData: DownloadData? = null) {
        val intent = Intent(context, DownloadService::class.java)
            .putExtra(IntentData.downloadData, downloadData)

        ContextCompat.startForegroundService(context, intent)
    }

    fun DownloadItem.getNotificationId(): Int {
        return Int.MAX_VALUE - id
    }
}
