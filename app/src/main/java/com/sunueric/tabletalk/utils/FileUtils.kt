package com.sunueric.tabletalk.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    /**
     * Resolves a Uri to a physical file path string.
     * Required for GGUF/Native C++ libraries on Android.
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        // Step 1: Check for Direct Path (common on Samsung Downloads/Documents)
        if (uri.path?.contains("primary") == true) {
            val split = uri.path?.split(":")
            if (split != null && split.size > 1) {
                val potentialPath = "/storage/emulated/0/${split[1]}"
                val file = File(potentialPath)
                if (file.exists()) return potentialPath
            }
        }

        // Step 2: Fallback - Copy to App Cache
        // Note: For a 3.3GB file, this will take time!
        return copyToInternalStorage(context, uri)
    }

    private fun copyToInternalStorage(context: Context, uri: Uri): String? {
        val returnCursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        returnCursor.close()

        val file = File(context.filesDir, name)

        // Save time: Don't re-copy if size matches
        if (file.exists() && file.length() > 0) return file.absolutePath

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}