package com.sunueric.tabletalk.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import java.io.File

object ModelDownloader {
    // The direct download link for the Q3_K_M GGUF file
    private const val MODEL_URL = "https://huggingface.co/tensorblock/RUCKBReasoning_TableLLM-7b-GGUF/resolve/main/TableLLM-7b-Q3_K_M.gguf?download=true"
    private const val FILE_NAME = "TableLLM-7b-Q3_K_M.gguf"
    private const val SUB_PATH = "Models/$FILE_NAME"

    fun downloadModel(context: Context) {
        // 1. Pre-check: Is there internet?
        if (!isNetworkAvailable(context)) {
            Toast.makeText(context, "No internet connection detected.", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = MODEL_URL.toUri()

        try {
            // 2. Pre-check: Create the directory manually to be safe
            // This prevents "Write" errors on some custom Android OS versions
            val destinationPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val modelDir = File(destinationPath, SUB_PATH)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val request = DownloadManager.Request(uri)
                .setTitle("Downloading TableLLM AI")
                .setDescription("Downloading 3.3GB model file...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(false) // Force WiFi (save user data)
                .setAllowedOverRoaming(false)
                // Save to: /storage/emulated/0/Download/Models/TableLLM-7b-Q3_K_M.gguf
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$SUB_PATH/$FILE_NAME")

            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started! Check notification tray.", Toast.LENGTH_LONG).show()

        } catch (e: SecurityException) {
            // This catches permission errors specifically
            Toast.makeText(context, "Permission Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            // This catches everything else (disk full, bad URL, etc.)
            Toast.makeText(context, "Download Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Helper to check network status gracefully
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
}