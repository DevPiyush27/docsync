/**
 * ==============================================================================
 * DocSync — Web Application Controller (Supabase JS v2)
 * ==============================================================================
 */

// 1. Default Supabase Credentials (replace with your project credentials or use the in-app Config modal)
const DEFAULT_SUPABASE_URL = "https://zqiozemfwidrlpksxbza.supabase.co";
const DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpxaW96ZW1md2lkcmxwa3N4YnphIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3ODQ1MzUsImV4cCI6MjEwNDM2MDUzNX0.p50TkFOysj6nKy1xbaQWwuV-SirkAhdL_EzqGs_rcZk";

// Maximum file size: 50 Megabytes in bytes
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 52,428,800 bytes
const STORAGE_BUCKET = "sync_uploads";
const TABLE_NAME = "sync_sessions";

// State
let supabaseClient = null;
let selectedFile = null;
let isUploading = false;

// DOM Elements
const syncForm = document.getElementById("sync-form");
const codeInput = document.getElementById("code-input");
const codeStatus = document.getElementById("code-status");
const dropZone = document.getElementById("drop-zone");
const fileInput = document.getElementById("file-input");
const dropZoneEmpty = document.getElementById("drop-zone-empty");
const filePreview = document.getElementById("file-preview");
const fileNameEl = document.getElementById("file-name");
const fileSizeEl = document.getElementById("file-size");
const removeFileBtn = document.getElementById("remove-file-btn");
const submitBtn = document.getElementById("submit-btn");
const btnSpinner = document.getElementById("btn-spinner");
const progressSection = document.getElementById("progress-section");
const progressBar = document.getElementById("progress-bar");
const progressStatus = document.getElementById("progress-status");
const progressPercent = document.getElementById("progress-percent");
const statusBanner = document.getElementById("status-banner");
const bannerMessage = document.getElementById("banner-message");

// Config Modal Elements
const configBtn = document.getElementById("config-btn");
const configModal = document.getElementById("config-modal");
const closeModalBtn = document.getElementById("close-modal-btn");
const cfgUrlInput = document.getElementById("cfg-url");
const cfgKeyInput = document.getElementById("cfg-key");
const saveConfigBtn = document.getElementById("save-config-btn");

/**
 * Initialize Supabase Client
 */
function initSupabase() {
    const savedUrl = localStorage.getItem("docsync_supabase_url") || DEFAULT_SUPABASE_URL;
    const savedKey = localStorage.getItem("docsync_supabase_key") || DEFAULT_SUPABASE_ANON_KEY;

    cfgUrlInput.value = savedUrl !== DEFAULT_SUPABASE_URL ? savedUrl : "";
    cfgKeyInput.value = savedKey !== DEFAULT_SUPABASE_ANON_KEY ? savedKey : "";

    try {
        if (window.supabase && savedUrl && savedKey) {
            supabaseClient = window.supabase.createClient(savedUrl, savedKey);
            console.log("Supabase client initialized successfully.");
        } else {
            console.warn("Supabase library not loaded or credentials missing.");
        }
    } catch (err) {
        console.error("Failed to initialize Supabase client:", err);
    }
}

/**
 * Format bytes into human readable format
 */
function formatBytes(bytes, decimals = 2) {
    if (!+bytes) return "0 Bytes";
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

/**
 * Show / Hide Status Banner
 */
function showBanner(message, type = "success") {
    statusBanner.className = `banner ${type}`;
    bannerMessage.textContent = message;
    statusBanner.classList.remove("hidden");
}

function hideBanner() {
    statusBanner.classList.add("hidden");
}

/**
 * Update Progress Bar
 */
function updateProgress(percent, message) {
    progressSection.classList.remove("hidden");
    progressBar.style.width = `${percent}%`;
    progressPercent.textContent = `${Math.round(percent)}%`;
    if (message) progressStatus.textContent = message;
}

function hideProgress() {
    progressSection.classList.add("hidden");
    progressBar.style.width = "0%";
    progressPercent.textContent = "0%";
}

/**
 * Handle File Selection & Validation
 */
function handleFile(file) {
    hideBanner();

    if (!file) {
        selectedFile = null;
        dropZoneEmpty.classList.remove("hidden");
        filePreview.classList.add("hidden");
        return;
    }

    // Validate size limit (50MB)
    if (file.size > MAX_FILE_SIZE_BYTES) {
        showBanner(
            `File is too large (${formatBytes(file.size)}). Maximum allowed size is 50MB.`,
            "error"
        );
        fileInput.value = "";
        selectedFile = null;
        dropZoneEmpty.classList.remove("hidden");
        filePreview.classList.add("hidden");
        return;
    }

    selectedFile = file;
    fileNameEl.textContent = file.name;
    fileSizeEl.textContent = formatBytes(file.size);
    dropZoneEmpty.classList.add("hidden");
    filePreview.classList.remove("hidden");
}

/**
 * Event Listeners: 6-Digit Code Input
 */
codeInput.addEventListener("input", (e) => {
    // Only permit digits
    const cleaned = e.target.value.replace(/\D/g, "").slice(0, 6);
    e.target.value = cleaned;

    const count = cleaned.length;
    codeStatus.querySelector(".digit-count").textContent = `${count}/6 digits`;

    if (count === 6) {
        codeInput.style.borderColor = "var(--success)";
    } else {
        codeInput.style.borderColor = "";
    }
});

/**
 * Event Listeners: Drag & Drop
 */
["dragenter", "dragover"].forEach((eventName) => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.add("dragover");
    });
});

["dragleave", "drop"].forEach((eventName) => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove("dragover");
    });
});

dropZone.addEventListener("drop", (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files.length > 0) {
        handleFile(files[0]);
    }
});

fileInput.addEventListener("change", (e) => {
    if (e.target.files && e.target.files.length > 0) {
        handleFile(e.target.files[0]);
    }
});

removeFileBtn.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    fileInput.value = "";
    handleFile(null);
});

/**
 * Form Submission: Upload to Supabase Storage & Insert into sync_sessions
 */
syncForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const code = codeInput.value.trim();
    if (code.length !== 6) {
        showBanner("Please enter a valid 6-digit sync code.", "error");
        codeInput.focus();
        return;
    }

    if (!selectedFile) {
        showBanner("Please select a file to transfer.", "error");
        return;
    }

    // Check credentials
    const currentUrl = localStorage.getItem("docsync_supabase_url") || DEFAULT_SUPABASE_URL;
    if (!currentUrl || currentUrl.includes("your-project-ref")) {
        showBanner("Please configure your Supabase URL & Anon Key via the Config button.", "error");
        configModal.classList.remove("hidden");
        return;
    }

    try {
        isUploading = true;
        submitBtn.disabled = true;
        btnSpinner.classList.remove("hidden");
        submitBtn.querySelector(".btn-text").classList.add("hidden");
        hideBanner();

        // 1. Prepare File Path & Upload
        updateProgress(20, "Uploading file to Supabase Storage...");
        const fileExt = selectedFile.name.split(".").pop();
        const cleanName = selectedFile.name.replace(/[^a-zA-Z0-9._-]/g, "_");
        const uniquePath = `sync_${code}_${Date.now()}_${cleanName}`;

        const { data: uploadData, error: uploadError } = await supabaseClient.storage
            .from(STORAGE_BUCKET)
            .upload(uniquePath, selectedFile, {
                cacheControl: "3600",
                upsert: true
            });

        if (uploadError) {
            throw new Error(`Storage upload failed: ${uploadError.message}`);
        }

        updateProgress(65, "Generating secure public URL...");

        // 2. Retrieve Public Download URL
        const { data: publicUrlData } = supabaseClient.storage
            .from(STORAGE_BUCKET)
            .getPublicUrl(uniquePath);

        const downloadUrl = publicUrlData?.publicUrl;
        if (!downloadUrl) {
            throw new Error("Failed to retrieve public URL for uploaded file.");
        }

        updateProgress(85, "Broadcasting sync session to Android device...");

        // 3. Insert record into sync_sessions table
        const { error: insertError } = await supabaseClient
            .from(TABLE_NAME)
            .insert([
                {
                    id: code,
                    download_url: downloadUrl,
                    file_name: selectedFile.name,
                    file_size: selectedFile.size
                }
            ]);

        if (insertError) {
            throw new Error(`Database insert failed: ${insertError.message}`);
        }

        // 4. Complete
        updateProgress(100, "Transferred successfully!");
        showBanner(
            `🚀 "${selectedFile.name}" successfully sent to Android device (${code})! Download has started.`,
            "success"
        );

        // Reset file selection
        fileInput.value = "";
        handleFile(null);

    } catch (err) {
        console.error("Transfer error:", err);
        showBanner(err.message || "An unexpected error occurred during transfer.", "error");
    } finally {
        isUploading = false;
        submitBtn.disabled = false;
        btnSpinner.classList.add("hidden");
        submitBtn.querySelector(".btn-text").classList.remove("hidden");
        setTimeout(() => {
            if (!isUploading) {
                progressSection.classList.add("hidden");
            }
        }, 3000);
    }
});

/**
 * Modal Event Listeners
 */
configBtn.addEventListener("click", () => {
    configModal.classList.remove("hidden");
});

closeModalBtn.addEventListener("click", () => {
    configModal.classList.add("hidden");
});

configModal.addEventListener("click", (e) => {
    if (e.target === configModal) {
        configModal.classList.add("hidden");
    }
});

saveConfigBtn.addEventListener("click", () => {
    const url = cfgUrlInput.value.trim();
    const key = cfgKeyInput.value.trim();

    if (!url || !key) {
        alert("Please provide both Supabase URL and Anon Key.");
        return;
    }

    localStorage.setItem("docsync_supabase_url", url);
    localStorage.setItem("docsync_supabase_key", key);

    initSupabase();
    configModal.classList.add("hidden");
    showBanner("Supabase credentials updated successfully!", "success");
});

// Initialize on page load
window.addEventListener("DOMContentLoaded", () => {
    initSupabase();
});
