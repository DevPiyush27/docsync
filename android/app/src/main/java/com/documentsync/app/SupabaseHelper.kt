package com.documentsync.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth // <-- 1. This import is new
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseHelper {
    val client = createSupabaseClient(
        supabaseUrl = "https://zqiozemfwidrlpksxbza.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpxaW96ZW1md2lkcmxwa3N4YnphIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3ODQ1MzUsImV4cCI6MjEwNDM2MDUzNX0.p50TkFOysj6nKy1xbaQWwuV-SirkAhdL_EzqGs_rcZk"
    ) {
        install(Auth) // <-- 2. This is the plugin the crash is asking for!
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}