package com.documentsync.app

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Globally scoped ViewModel that bridges live DownloadManager broadcasts with startup queries.
 *
 * - Uses [replay = 1] on [MutableSharedFlow] so startup events aren't dropped before Compose subscribes.
 * - Tracks notified download IDs in SharedPreferences to prevent duplicate pop-ups.
 * - [syncDownloadedFiles] catches unhandled completions from when the app was offline or closed.
 * - Keeps a RECEIVER_EXPORTED BroadcastReceiver active for real-time downloads while the app runs.
 */
class DownloadNotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("docsync_notified_downloads", Context.MODE_PRIVATE)
    private val keyNotifiedIds = "notified_download_ids"

    // 1. replay = 1 ensures events emitted on startup aren't lost before Compose subscribes
    private val _downloadEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    val downloadEvents: SharedFlow<String> = _downloadEvents.asSharedFlow()

    private val downloadManager: DownloadManager? =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    // 2. Real-time receiver for downloads finishing while app is running
    private val downloadReceiver = object : BroadcastReceiver() {
        @SuppressLint("Range")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L) return

            val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val query = DownloadManager.Query().setFilterById(downloadId)

            try {
                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // Check if already notified
                            if (!isAlreadyNotified(downloadId)) {
                                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                val rawTitle = if (titleIndex != -1) cursor.getString(titleIndex) else null
                                val fileTitle = rawTitle?.takeIf { it.isNotBlank() } ?: "Document_$downloadId"

                                Log.d("DownloadNotificationVM", "Live download completed: $fileTitle")
                                markAsNotified(downloadId)
                                _downloadEvents.tryEmit(fileTitle)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadNotificationVM", "Error querying downloadId #$downloadId: ${e.message}", e)
            }
        }
    }

    init {
        // Register receiver for real-time downloads
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            application,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /**
     * Queries DownloadManager for all STATUS_SUCCESSFUL downloads.
     * Catches any downloads that finished while the app was offline or closed.
     * Emits unnotified titles and marks them as notified in SharedPreferences.
     */
    @SuppressLint("Range")
    fun syncDownloadedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val dm = downloadManager ?: return@launch
            val query = DownloadManager.Query().apply {
                setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
            }

            try {
                dm.query(query)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

                    while (cursor.moveToNext()) {
                        val downloadId = if (idIndex != -1) cursor.getLong(idIndex) else continue

                        if (!isAlreadyNotified(downloadId)) {
                            val rawTitle = if (titleIndex != -1) cursor.getString(titleIndex) else null
                            val fileTitle = rawTitle?.takeIf { it.isNotBlank() } ?: "Document_$downloadId"

                            Log.d("DownloadNotificationVM", "Startup sync discovered unnotified download: $fileTitle")
                            markAsNotified(downloadId)
                            _downloadEvents.emit(fileTitle)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadNotificationVM", "Error in syncDownloadedFiles: ${e.message}", e)
            }
        }
    }

    private fun getNotifiedIds(): MutableSet<String> {
        return prefs.getStringSet(keyNotifiedIds, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun markAsNotified(downloadId: Long) {
        val set = getNotifiedIds()
        set.add(downloadId.toString())
        prefs.edit().putStringSet(keyNotifiedIds, set).apply()
    }

    private fun isAlreadyNotified(downloadId: Long): Boolean {
        return getNotifiedIds().contains(downloadId.toString())
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Log.w("DownloadNotificationVM", "Receiver unregister error: ${e.message}")
        }
    }
}
