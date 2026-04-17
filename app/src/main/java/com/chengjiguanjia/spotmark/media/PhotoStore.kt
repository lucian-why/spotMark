package com.chengjiguanjia.spotmark.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class PhotoStore(
    private val context: Context,
) {
    private val photoDir: File
        get() = File(context.filesDir, "spot_photos").also { it.mkdirs() }

    private val cameraDir: File
        get() = File(context.cacheDir, "camera").also { it.mkdirs() }

    fun createCameraUri(): Uri {
        val photoFile = File(cameraDir, "spot_camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
    }

    fun savePhoto(source: Uri): String {
        val target = File(photoDir, "spot_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open photo source" }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target.absolutePath
    }

    fun deletePhoto(path: String) {
        File(path).delete()
    }

    fun deletePhotos(paths: List<String>) {
        paths.forEach { path -> File(path).delete() }
    }
}
