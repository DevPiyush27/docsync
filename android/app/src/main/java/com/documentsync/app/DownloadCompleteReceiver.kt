package com.documentsync.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * BroadcastReceiver listening for DownloadManager.ACTION_DOWNLOAD_COMPLETE.
 *
 * It extracts the download ID, queries DownloadManager to confirm STATUS_SUCCESSFUL,
 * retrieves the file metadata, and invokes [onDownloadCompleted].
 *
 * NOTE: This receiver does not cancel, dismiss, or touch native DownloadManager notifications,
 * allowing Android's system status-bar notification to function unimpeded.
 */
class DownloadCompleteReceiver(
    private val onDownloadCompleted: (CompletedTransfer) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "DocSyncDownloadReceiver"
    }

    @SuppressLint("Range")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) {
            Log.w(TAG, "Received ACTION_DOWNLOAD_COMPLETE with invalid ID")
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager service not available")
            return
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        try {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    Log.w(TAG, "No download record found for ID: $downloadId")
                    return
                }

                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val mimeTypeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)

                    val rawTitle = if (titleIndex != -1) cursor.getString(titleIndex) else null
                    val rawLocalUri = if (localUriIndex != -1) cursor.getString(localUriIndex) else null
                    val fileSize = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    val mimeType = if (mimeTypeIndex != -1) cursor.getString(mimeTypeIndex) else null

                    // Prefer content:// URI from DownloadManager for Scoped Storage compatibility
                    val fileUri: Uri? = try {
                        downloadManager.getUriForDownloadedFile(downloadId)
                            ?: rawLocalUri?.let { Uri.parse(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not resolve content URI via getUriForDownloadedFile: ${e.message}")
                        rawLocalUri?.let { Uri.parse(it) }
                    }

                    // Fallback to URI filename if title is blank or missing
                    val resolvedName = rawTitle?.takeIf { it.isNotBlank() }
                        ?: fileUri?.lastPathSegment
                        ?: "file_$downloadId"

                    val transfer = CompletedTransfer(
                        downloadId = downloadId,
                        fileName = resolvedName,
                        fileUri = fileUri,
                        fileSize = fileSize,
                        mimeType = mimeType,
                        timestamp = System.currentTimeMillis()
                    )

                    Log.i(TAG, "Download #$downloadId verified successful: ${transfer.fileName} (${transfer.fileSize} bytes)")
                    onDownloadCompleted(transfer)
                } else {
                    Log.d(TAG, "Download #$downloadId completed with non-success status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying DownloadManager for download ID $downloadId: ${e.message}", e)
        }
    }
}
