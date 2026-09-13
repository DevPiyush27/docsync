package com.documentsync.app

import android.net.Uri
import java.util.UUID

/**
 * Represents a successfully completed file transfer handled by native DownloadManager.
 */
data class CompletedTransfer(
    val id: String = UUID.randomUUID().toString(),
    val downloadId: Long,
    val fileName: String,
    val fileUri: Uri?,
    val fileSize: Long = 0L,
    val mimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * UI State for the Recent Transfers list.
 */
data class TransfersUiState(
    val transfers: List<CompletedTransfer> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * One-off UI events (consumed once, not retained across recompositions or config changes).
 */
sealed interface TransferUiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = "Open",
        val transfer: CompletedTransfer
    ) : TransferUiEvent

    data class ShowToast(
        val message: String
    ) : TransferUiEvent
}
