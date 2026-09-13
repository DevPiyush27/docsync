package com.documentsync.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lifecycle-aware permission requester for Android 13+ (API 33+) POST_NOTIFICATIONS.
 */
@Composable
fun RequestNotificationPermissionEffect(
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: () -> Unit = {}
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) onPermissionGranted() else onPermissionDenied()
        }

        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Main Composable demonstrating state-driven notifications,
 * StateFlow collection, and LazyColumn rendering.
 */
@Composable
fun RecentTransfersScreen(
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 1. Observe the notification state safely
    val activeNotification by viewModel.activeNotification.collectAsStateWithLifecycle()

    // 2. Invoke active Coroutine polling loop on app/screen start
    LaunchedEffect(Unit) {
        viewModel.startDownloadObserver(context)
    }

    // 3. The Handshake: Display Snackbar, then clear the state
    LaunchedEffect(activeNotification) {
        activeNotification?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            // Tell ViewModel we successfully showed it
            viewModel.clearNotification()
        }
    }

    // 4. Collect StateFlow list of completed transfers
    val recentTransfers by viewModel.recentTransfers.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF060913),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Recent Transfers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                }

                if (recentTransfers.isNotEmpty()) {
                    Text(
                        text = "${recentTransfers.size} completed",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Recent Transfers List or Empty State
            AnimatedVisibility(
                visible = recentTransfers.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyTransfersCard()
            }

            AnimatedVisibility(
                visible = recentTransfers.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = recentTransfers,
                        key = { it.downloadId }
                    ) { transfer ->
                        TransferItemCard(
                            transfer = transfer,
                            onClick = { openDownloadedFile(context, transfer) },
                            onDismiss = { viewModel.removeTransfer(transfer.downloadId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modular Composable Section designed to drop directly into existing screens.
 */
@Composable
fun RecentTransfersSection(
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = viewModel(),
    onShowSnackbar: (suspend (message: String) -> Unit)? = null
) {
    val context = LocalContext.current

    // Observe the notification state safely
    val activeNotification by viewModel.activeNotification.collectAsStateWithLifecycle()

    // 1. Invoke active Coroutine polling loop on start
    LaunchedEffect(Unit) {
        viewModel.startDownloadObserver(context)
    }

    // 2. The Handshake for the section component
    LaunchedEffect(activeNotification) {
        activeNotification?.let { message ->
            if (onShowSnackbar != null) {
                onShowSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            // Tell ViewModel we successfully showed it
            viewModel.clearNotification()
        }
    }

    // 4. Reactive state collection
    val recentTransfers by viewModel.recentTransfers.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Transfers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            if (recentTransfers.isNotEmpty()) {
                Text(
                    text = "${recentTransfers.size} files",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (recentTransfers.isEmpty()) {
            EmptyTransfersCard()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = recentTransfers,
                    key = { it.downloadId }
                ) { transfer ->
                    TransferItemCard(
                        transfer = transfer,
                        onClick = { openDownloadedFile(context, transfer) },
                        onDismiss = { viewModel.removeTransfer(transfer.downloadId) }
                    )
                }
            }
        }
    }
}

/**
 * Card representing a completed transfer item.
 */
@Composable
fun TransferItemCard(
    transfer: CompletedTransfer,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(transfer.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(transfer.timestamp))
    }

    val formattedSize = remember(transfer.fileSize) {
        formatBytes(transfer.fileSize)
    }

    val fileIcon = remember(transfer.fileName) {
        getTransferFileIcon(transfer.fileName)
    }

    val cardBorder = Brush.linearGradient(
        listOf(
            Color(0xFF6366F1).copy(alpha = 0.5f),
            Color(0xFF8B5CF6).copy(alpha = 0.3f),
            Color(0xFFD946EF).copy(alpha = 0.15f)
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gradient Icon Box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFD946EF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (formattedSize.isNotBlank()) "$formattedSize • $formattedTime" else formattedTime,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Dismiss Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Placeholder when no files have completed downloading yet.
 */
@Composable
fun EmptyTransfersCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF25304C), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No transfers completed yet",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Files downloaded via OfflineSyncWorker or DownloadManager will appear here automatically.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * Helper to launch an intent to view/open the downloaded file.
 */
fun openDownloadedFile(context: Context, transfer: CompletedTransfer) {
    val uri = transfer.fileUri ?: return
    val mime = transfer.mimeType ?: context.contentResolver.getType(uri) ?: "*/*"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No compatible app found to open ${transfer.fileName}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Formats byte size into human-readable format.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.size - 1)
    val size = bytes / Math.pow(1024.0, groupIndex.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", size, units[groupIndex])
}

/**
 * Helper to select an appropriate vector icon according to file extension.
 */
fun getTransferFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> Icons.Default.PictureAsPdf
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Default.Image
        "mp4", "mkv", "mov", "webm", "avi" -> Icons.Default.Videocam
        "mp3", "wav", "flac", "m4a", "ogg" -> Icons.Default.MusicNote
        else -> Icons.Default.InsertDriveFile
    }
}