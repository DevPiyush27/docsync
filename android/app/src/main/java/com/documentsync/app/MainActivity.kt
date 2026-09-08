package com.documentsync.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

// ==============================================================================
// 1. Data Models
// ==============================================================================

@Serializable
data class SyncSession(
    val id: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

data class DownloadHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val downloadUrl: String,
    val fileSize: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Saved to Downloads"
)

sealed interface SyncStatus {
    data object Disconnected : SyncStatus
    data object Connecting : SyncStatus
    data class Listening(val code: String) : SyncStatus
    data class TransferReceived(val fileName: String) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

// ==============================================================================
// 2. Default Configuration Constants
// ==============================================================================

private const val PREFS_NAME = "docsync_prefs"
private const val KEY_SUPABASE_URL = "supabase_url"
private const val KEY_SUPABASE_KEY = "supabase_anon_key"

private const val DEFAULT_SUPABASE_URL = "https://zqiozemfwidrlpksxbza.supabase.co"
private const val DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpxaW96ZW1md2lkcmxwa3N4YnphIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3ODQ1MzUsImV4cCI6MjEwNDM2MDUzNX0.p50TkFOysj6nKy1xbaQWwuV-SirkAhdL_EzqGs_rcZk"

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

// Colors
val BgMain = Color(0xFF060913)
val BgCard = Color(0xFF0F172A).copy(alpha = 0.78f)
val PrimaryIndigo = Color(0xFF6366F1)
val SecondaryViolet = Color(0xFF8B5CF6)
val AccentPink = Color(0xFFD946EF)
val SuccessEmerald = Color(0xFF10B981)
val TextMuted = Color(0xFF94A3B8)
val BorderSubtle = Color(0xFF25304C)

val AccentGradient = Brush.linearGradient(
    listOf(PrimaryIndigo, SecondaryViolet, AccentPink)
)

val CardBorderGradient = Brush.linearGradient(
    listOf(
        PrimaryIndigo.copy(alpha = 0.55f),
        SecondaryViolet.copy(alpha = 0.35f),
        AccentPink.copy(alpha = 0.15f)
    )
)

// ==============================================================================
// 3. Main Activity
// ==============================================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgMain
                ) {
                    DocSyncApp()
                }
            }
        }
    }
}

// ==============================================================================
// 4. Main Compose Application Screen
// ==============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocSyncApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // State
    var supabaseUrl by remember { mutableStateOf(prefs.getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL) }
    var supabaseAnonKey by remember { mutableStateOf(prefs.getString(KEY_SUPABASE_KEY, DEFAULT_SUPABASE_ANON_KEY) ?: DEFAULT_SUPABASE_ANON_KEY) }
    var currentCode by remember { mutableStateOf(generate6DigitCode()) }
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Disconnected) }
    val historyItems = remember { mutableStateListOf<DownloadHistoryItem>() }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Supabase Client and Active Realtime Subscription Job
    var supabaseClient by remember { mutableStateOf<SupabaseClient?>(null) }
    var realtimeJob by remember { mutableStateOf<Job?>(null) }

    // Function to re-initialize Supabase & Realtime Listener for the active code
    fun startListening(code: String) {
        realtimeJob?.cancel()

        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project-ref") || supabaseAnonKey.isBlank()) {
            syncStatus = SyncStatus.Error("Please configure valid Supabase credentials in Settings.")
            return
        }

        realtimeJob = coroutineScope.launch {
            try {
                syncStatus = SyncStatus.Connecting
                
                val client = createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseAnonKey
                ) {
                    install(Postgrest)
                    install(Realtime)
                }
                supabaseClient = client

                val channel = client.channel("sync-session-$code")
                
                val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "sync_sessions"
                    filter(FilterOperation("id", FilterOperator.EQ, code))
                }

                channel.subscribe()
                syncStatus = SyncStatus.Listening(code)

                changeFlow
                    .catch { e ->
                        syncStatus = SyncStatus.Error("Realtime error: ${e.localizedMessage}")
                    }
                    .collect { insertAction ->
                        try {
                            val session = try {
                                json.decodeFromJsonElement<SyncSession>(insertAction.record)
                            } catch (ex: Exception) {
                                val downloadUrl = insertAction.record["download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                                val rawName = insertAction.record["file_name"]?.jsonPrimitive?.contentOrNull
                                val rawSize = insertAction.record["file_size"]?.jsonPrimitive?.longOrNull
                                SyncSession(
                                    id = code,
                                    downloadUrl = downloadUrl,
                                    fileName = rawName,
                                    fileSize = rawSize
                                )
                            }
                            val fileName = session.fileName ?: "synced_doc_${System.currentTimeMillis()}"
                            
                            syncStatus = SyncStatus.TransferReceived(fileName)
                            
                            // Native DownloadManager file download
                            enqueueDownload(context, session.downloadUrl, fileName)

                            // Add to history
                            historyItems.add(
                                0,
                                DownloadHistoryItem(
                                    fileName = fileName,
                                    downloadUrl = session.downloadUrl,
                                    fileSize = session.fileSize,
                                    status = "Saved to Downloads"
                                )
                            )

                            // Reset status to listening after 4 seconds
                            delay(4000)
                            syncStatus = SyncStatus.Listening(code)
                        } catch (ex: Exception) {
                            syncStatus = SyncStatus.Error("Decode error: ${ex.localizedMessage}")
                        }
                    }
            } catch (e: Exception) {
                syncStatus = SyncStatus.Error("Connection failed: ${e.localizedMessage}")
            }
        }
    }

    LaunchedEffect(currentCode, supabaseUrl, supabaseAnonKey) {
        startListening(currentCode)
    }

    DisposableEffect(Unit) {
        onDispose {
            realtimeJob?.cancel()
        }
    }

    Scaffold(
        containerColor = BgMain,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Doc",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sync",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = PrimaryIndigo
                                )
                            }
                            Text(
                                text = "Realtime Web-to-Android Node",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFC7D2FE)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgMain
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Hero Pairing Code Card
            PairingCodeCard(
                code = currentCode,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("DocSync Pairing Code", currentCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Pairing code $currentCode copied!", Toast.LENGTH_SHORT).show()
                },
                onRegenerate = {
                    currentCode = generate6DigitCode()
                    Toast.makeText(context, "Generated new code: $currentCode", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Realtime Connection Status Pill
            ConnectionStatusBadge(status = syncStatus)

            Spacer(modifier = Modifier.height(24.dp))

            // Download Activity Section Header
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
                if (historyItems.isNotEmpty()) {
                    Text(
                        text = "${historyItems.size} files",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (historyItems.isEmpty()) {
                EmptyStateCard()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyItems, key = { it.id }) { item ->
                        DownloadItemCard(item = item)
                    }
                }
            }
        }
    }

    // Supabase Configuration Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentUrl = supabaseUrl,
            currentKey = supabaseAnonKey,
            onDismiss = { showSettingsDialog = false },
            onSave = { newUrl, newKey ->
                supabaseUrl = newUrl
                supabaseAnonKey = newKey
                prefs.edit()
                    .putString(KEY_SUPABASE_URL, newUrl)
                    .putString(KEY_SUPABASE_KEY, newKey)
                    .apply()
                showSettingsDialog = false
                Toast.makeText(context, "Supabase settings saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ==============================================================================
// 5. Compose UI Components
// ==============================================================================

@Composable
fun PairingCodeCard(
    code: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = CardBorderGradient,
                shape = RoundedCornerShape(26.dp)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Chip Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PrimaryIndigo.copy(alpha = 0.15f))
                    .border(1.dp, PrimaryIndigo.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DEVICE PAIRING CODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5B4FC),
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Formatted 6-Digit Neon Tiles
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                code.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgMain)
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(PrimaryIndigo.copy(alpha = 0.6f), SecondaryViolet.copy(alpha = 0.25f))
                                ),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Enter this 6-digit code on the DocSync web portal to transfer documents directly to this device.",
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Code", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onRegenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "New Code",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Code", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBadge(status: SyncStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val (bgColor, borderColor, textColor, dotColor, label) = when (status) {
        is SyncStatus.Disconnected -> StatusStyle(
            Color(0xFF1E293B).copy(alpha = 0.6f),
            Color(0xFF334155),
            Color(0xFF94A3B8),
            Color(0xFF64748B),
            "Disconnected"
        )
        is SyncStatus.Connecting -> StatusStyle(
            Color(0xFF1E3A8A).copy(alpha = 0.25f),
            PrimaryIndigo.copy(alpha = 0.6f),
            Color(0xFFC7D2FE),
            PrimaryIndigo,
            "Connecting to Realtime Channel..."
        )
        is SyncStatus.Listening -> StatusStyle(
            SuccessEmerald.copy(alpha = 0.15f),
            SuccessEmerald.copy(alpha = 0.5f),
            Color(0xFF6EE7B7),
            SuccessEmerald,
            "Listening • Ready for code ${status.code}"
        )
        is SyncStatus.TransferReceived -> StatusStyle(
            AccentPink.copy(alpha = 0.2f),
            AccentPink.copy(alpha = 0.6f),
            Color(0xFFFBCFE8),
            AccentPink,
            "Receiving: ${status.fileName}"
        )
        is SyncStatus.Error -> StatusStyle(
            Color(0xFF7F1D1D).copy(alpha = 0.25f),
            Color(0xFFEF4444).copy(alpha = 0.5f),
            Color(0xFFFCA5A5),
            Color(0xFFEF4444),
            status.message
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(if (status is SyncStatus.Listening || status is SyncStatus.Connecting || status is SyncStatus.TransferReceived) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class StatusStyle(
    val bg: Color,
    val border: Color,
    val text: Color,
    val dot: Color,
    val label: String
)

@Composable
fun DownloadItemCard(item: DownloadHistoryItem) {
    val formattedTime = remember(item.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    val fileIcon = remember(item.fileName) {
        getFileIconForName(item.fileName)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${item.status} • $formattedTime",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun getFileIconForName(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> Icons.Default.PictureAsPdf
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Default.Image
        "mp4", "mkv", "mov", "webm", "avi" -> Icons.Default.Videocam
        "mp3", "wav", "flac", "m4a", "ogg" -> Icons.Default.MusicNote
        else -> Icons.Default.InsertDriveFile
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard.copy(alpha = 0.45f))
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
                    tint = TextMuted,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No transfers yet",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Files sent from the web app will appear here and download automatically to your public Downloads folder.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ==============================================================================
// 6. Settings Dialog
// ==============================================================================

@Composable
fun SettingsDialog(
    currentUrl: String,
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Supabase Configuration",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Configure your Supabase project credentials for real-time document synchronization.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Supabase URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextMuted
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Supabase Anon Key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextMuted
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlText.trim(), keyText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

// ==============================================================================
// 7. Helpers & Native Download Engine
// ==============================================================================

private fun generate6DigitCode(): String {
    return Random.nextInt(100000, 999999).toString()
}

private fun enqueueDownload(context: Context, downloadUrl: String, rawFileName: String) {
    try {
        val uri = Uri.parse(downloadUrl)
        val cleanFileName = rawFileName.ifBlank {
            uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        }

        val request = DownloadManager.Request(uri).apply {
            setTitle(cleanFileName)
            setDescription("Syncing document from DocSync Web")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanFileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(
            context,
            "📥 Downloading \"$cleanFileName\" to Downloads folder...",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Failed to start download: ${e.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// ==============================================================================
// 8. Compose Theme
// ==============================================================================

@Composable
fun DocSyncTheme(content: @Composable () -> Unit) {
    val darkScheme = darkColorScheme(
        primary = PrimaryIndigo,
        secondary = SecondaryViolet,
        tertiary = AccentPink,
        background = BgMain,
        surface = BgCard
    )

    MaterialTheme(
        colorScheme = darkScheme,
        content = content
    )
}
