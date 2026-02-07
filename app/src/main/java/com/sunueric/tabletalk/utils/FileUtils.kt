package com.sunueric.tabletalk.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object FileUtils {
    private const val TAG = "FileUtils"
    
    /**
     * Resolves a Uri to a physical file path string.
     * Required for GGUF/Native C++ libraries on Android.
     * 
     * On Android 10+, direct file paths may not be readable due to scoped storage.
     * This function checks if the file is actually readable before returning the path.
     * Requires MANAGE_EXTERNAL_STORAGE permission on Android 11+.
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        Log.d(TAG, "getPathFromUri called with: $uri")
        
        // Step 1: Check for Direct Path (common on Samsung Downloads/Documents)
        if (uri.path?.contains("primary") == true) {
            val split = uri.path?.split(":")
            if (split != null && split.size > 1) {
                val potentialPath = "/storage/emulated/0/${split[1]}"
                val file = File(potentialPath)
                Log.d(TAG, "Direct path: $potentialPath")
                Log.d(TAG, "File exists: ${file.exists()}, canRead: ${file.canRead()}")
                
                // Return direct path if we can read it (requires storage permission)
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Using direct path (readable)")
                    return potentialPath
                } else if (file.exists()) {
                    Log.w(TAG, "File exists but cannot read - storage permission needed")
                }
            }
        }

        // Step 2: Try to get path from document URI
        Log.d(TAG, "Trying document provider path extraction...")
        return getDocumentPath(context, uri)
    }
    
    /**
     * Try to extract path from document provider URI
     */
    private fun getDocumentPath(context: Context, uri: Uri): String? {
        try {
            // Get the file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val fileName = it.getString(nameIndex)
                        Log.d(TAG, "File name from URI: $fileName")
                        
                        // Common download locations to check
                        val commonPaths = listOf(
                            "/storage/emulated/0/Download/$fileName",
                            "/storage/emulated/0/Download/Models/$fileName",
                            "/storage/emulated/0/Documents/$fileName",
                            "/sdcard/Download/$fileName",
                            "/sdcard/Download/Models/$fileName"
                        )
                        
                        for (path in commonPaths) {
                            val file = File(path)
                            if (file.exists() && file.canRead()) {
                                Log.d(TAG, "Found readable file at: $path")
                                return path
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting document path", e)
        }
        
        Log.e(TAG, "Could not find readable path for URI")
        return null
    }
}