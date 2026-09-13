package com.documentsync.app

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TransfersViewModel(application: Application) : AndroidViewModel(application) {

    private val _notifiedDownloadIds = MutableStateFlow<Set<Long>>(emptySet())

    // 1. The memory bank for deleted/hidden files
    private val _dismissedDownloadIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _activeNotification = MutableStateFlow<String?>(null)
    val activeNotification: StateFlow<String?> = _activeNotification.asStateFlow()

    private val _recentTransfers = MutableStateFlow<List<CompletedTransfer>>(emptyList())
    val recentTransfers: StateFlow<List<CompletedTransfer>> = _recentTransfers.asStateFlow()

    private val _uiState = MutableStateFlow(TransfersUiState())
    val uiState: StateFlow<TransfersUiState> = _uiState.asStateFlow()

    private var observerJob: Job? = null

    // Globally accessible prefs for this ViewModel
    private val prefs = application.getSharedPreferences("DocSyncPrefs", Context.MODE_PRIVATE)

    init {
        // Load the permanent list of deleted files the moment the app starts
        val savedDismissedIds = prefs.getStringSet("dismissed_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        _dismissedDownloadIds.value = savedDismissedIds
    }

    @SuppressLint("Range")
    fun startDownloadObserver(context: Context) {
        if (observerJob?.isActive == true) return

        observerJob = viewModelScope.launch(Dispatchers.IO) {
            val downloadManager = context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) return@launch

            val isInitialized = prefs.getBoolean("is_initialized", false)

            if (!isInitialized) {
                try {
                    downloadManager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val existingIds = mutableSetOf<Long>()
                        while (cursor.moveToNext()) {
                            if (idIndex != -1) existingIds.add(cursor.getLong(idIndex))
                        }
                        _notifiedDownloadIds.value = existingIds
                        prefs.edit()
                            .putStringSet("notified_ids", existingIds.map { it.toString() }.toSet())
                            .putBoolean("is_initialized", true)
                            .apply()
                    }
                } catch (e: Exception) { Log.e("TransfersVM", "Pre-load error", e) }
            } else {
                val savedIds = prefs.getStringSet("notified_ids", emptySet())
                    ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
                _notifiedDownloadIds.value = savedIds
            }

            delay(2000)

            while (isActive) {
                try {
                    val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
                    downloadManager.query(query)?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val mimeTypeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                        val lastModIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)

                        val currentTransfers = mutableListOf<CompletedTransfer>()

                        while (cursor.moveToNext()) {
                            val downloadId = if (idIndex != -1) cursor.getLong(idIndex) else continue

                            // 2. BOUNCER CHECK: If the file was dismissed, skip it!
                            if (_dismissedDownloadIds.value.contains(downloadId)) continue

                            val rawTitle = if (titleIndex != -1) cursor.getString(titleIndex) else null
                            val rawLocalUri = if (localUriIndex != -1) cursor.getString(localUriIndex) else null

                            val fileUri: Uri? = try {
                                downloadManager.getUriForDownloadedFile(downloadId) ?: rawLocalUri?.let { Uri.parse(it) }
                            } catch (e: Exception) { rawLocalUri?.let { Uri.parse(it) } }

                            val fileTitle = rawTitle?.takeIf { it.isNotBlank() } ?: fileUri?.lastPathSegment ?: "Document_$downloadId"

                            currentTransfers.add(
                                CompletedTransfer(
                                    downloadId = downloadId,
                                    fileName = fileTitle,
                                    fileUri = fileUri,
                                    fileSize = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L,
                                    mimeType = if (mimeTypeIndex != -1) cursor.getString(mimeTypeIndex) else null,
                                    timestamp = if (lastModIndex != -1) cursor.getLong(lastModIndex) else System.currentTimeMillis()
                                )
                            )

                            if (!_notifiedDownloadIds.value.contains(downloadId)) {
                                val newSet = _notifiedDownloadIds.value + downloadId
                                _notifiedDownloadIds.value = newSet
                                prefs.edit().putStringSet("notified_ids", newSet.map { it.toString() }.toSet()).apply()
                                _activeNotification.value = "File $fileTitle downloaded successfully"
                            }
                        }

                        val sorted = currentTransfers.sortedByDescending { it.timestamp }
                        _recentTransfers.value = sorted
                        _uiState.update { current -> current.copy(transfers = sorted) }
                    }
                } catch (e: Exception) { Log.e("TransfersVM", "Polling error", e) }

                delay(1500)
            }
        }
    }

    fun clearNotification() {
        _activeNotification.value = null
    }

    fun stopDownloadObserver() {
        observerJob?.cancel()
        observerJob = null
    }

    fun removeTransfer(downloadId: Long) {
        // 1. Add to the permanent blocklist so it never shows in the UI again
        val updatedDismissed = _dismissedDownloadIds.value + downloadId
        _dismissedDownloadIds.value = updatedDismissed
        prefs.edit().putStringSet("dismissed_ids", updatedDismissed.map { it.toString() }.toSet()).apply()

        // 2. Obliterate it from Android's internal Ghost DB
        try {
            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
        } catch (e: Exception) {
            Log.e("TransfersVM", "Failed to clear ghost record from system", e)
        }

        // 3. Erase from the screen instantly
        _recentTransfers.update { current -> current.filterNot { it.downloadId == downloadId } }
        _uiState.update { current -> current.copy(transfers = _recentTransfers.value) }
    }

    fun clearTransfers() {
        _recentTransfers.value = emptyList()
        _uiState.update { it.copy(transfers = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        stopDownloadObserver()
    }
}