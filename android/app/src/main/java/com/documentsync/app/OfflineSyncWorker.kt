package com.documentsync.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.github.jan.supabase.auth.auth // <-- Required for auth checks
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

@Serializable
data class SyncQueueItem(
    val id: String, // MUST be a String to handle UUIDs correctly
    val file_name: String,
    val file_path: String,
    val status: String
)

class OfflineSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.e("DocSyncWorker", "🚀 WORKER IS AWAKE AND CHECKING QUEUE!")

        return try {
            // 1. THE MISSING LINK: Tell the worker to wait for the saved token to load!
            SupabaseHelper.client.auth.awaitInitialization()

            // 2. Prove to ourselves that the worker successfully grabbed the login session
            val user = SupabaseHelper.client.auth.currentUserOrNull()
            if (user == null) {
                Log.e("DocSyncWorker", "❌ Worker forgot who is logged in! RLS will block us.")
                return Result.retry()
            }
            Log.e("DocSyncWorker", "✅ Worker is successfully logged in as: ${user.email}")

            // 3. Now that we have the token, check the queue
            val pendingFiles = SupabaseHelper.client.postgrest["sync_queue"]
                .select { filter { eq("status", "pending") } }
                .decodeList<SyncQueueItem>()

            if (pendingFiles.isEmpty()) {
                Log.e("DocSyncWorker", "Queue is empty. Going back to sleep.")
                return Result.success()
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            for (file in pendingFiles) {
                Log.e("DocSyncWorker", "Generating Signed URL for: ${file.file_name}")

                val signedUrl = SupabaseHelper.client.storage["sync_uploads"].createSignedUrl(file.file_path, 5.hours)

                val request = DownloadManager.Request(Uri.parse(signedUrl)).apply {
                    setTitle(file.file_name)
                    setDescription("DocSync Offline Transfer")
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file.file_name)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                }

                downloadManager.enqueue(request)
                Log.e("DocSyncWorker", "Download triggered natively for: ${file.file_name}")

                // Mark as completed
                SupabaseHelper.client.postgrest["sync_queue"].update(
                    { set("status", "completed") }
                ) { filter { eq("id", file.id) } }
            }

            Result.success()

        } catch (e: Exception) {
            Log.e("DocSyncWorker", "❌ WORKER CRASHED: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }
}