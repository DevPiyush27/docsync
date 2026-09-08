/**
 * ==============================================================================
 * DocSync — Web Application Controller (Supabase JS v2)
 * Theme: Deep Midnight Blue / Indigo & Violet Glow + Supabase Auth
 * ==============================================================================
 */

// 1. Default Supabase Credentials
const DEFAULT_SUPABASE_URL = "https://zqiozemfwidrlpksxbza.supabase.co";
const DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpxaW96ZW1md2lkcmxwa3N4YnphIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3ODQ1MzUsImV4cCI6MjEwNDM2MDUzNX0.p50TkFOysj6nKy1xbaQWwuV-SirkAhdL_EzqGs_rcZk";

// Maximum file size: 50 Megabytes in bytes
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 52,428,800 bytes
const STORAGE_BUCKET = "sync_uploads";
const TABLE_NAME = "sync_sessions";

// State
let supabaseClient = null;
let currentUser = null;
let authMode = "login"; // "login" or "signup"
let selectedFile = null;
let isUploading = false;
let isAuthenticating = false;
let dragCounter = 0;

// DOM Elements — Main Card & Transfer
const transferCard = document.getElementById("transfer-card");
const infoGrid = document.getElementById("info-grid");
const syncForm = document.getElementById("sync-form");
const codeInput = document.getElementById("code-input");
const codeStatus = document.getElementById("code-status");
const dropZone = document.getElementById("drop-zone");
const fileInput = document.getElementById("file-input");
const dropZoneEmpty = document.getElementById("drop-zone-empty");
const filePreview = document.getElementById("file-preview");
const fileNameEl = document.getElementById("file-name");
const fileSizeEl = document.getElementById("file-size");
const fileTypeIconEl = document.getElementById("file-type-icon");
const removeFileBtn = document.getElementById("remove-file-btn");
const submitBtn = document.getElementById("submit-btn");
const btnSpinner = document.getElementById("btn-spinner");
const progressSection = document.getElementById("progress-section");
const progressBar = document.getElementById("progress-bar");
const progressStatus = document.getElementById("progress-status");
const progressPercent = document.getElementById("progress-percent");
const statusBanner = document.getElementById("status-banner");
const bannerMessage = document.getElementById("banner-message");
const bannerIcon = document.getElementById("banner-icon");

// DOM Elements — Auth & User Controls
const authCard = document.getElementById("auth-card");
const authForm = document.getElementById("auth-form");
const tabLogin = document.getElementById("tab-login");
const tabSignup = document.getElementById("tab-signup");
const authEmailInput = document.getElementById("auth-email");
const authPasswordInput = document.getElementById("auth-password");
const authSubmitBtn = document.getElementById("auth-submit-btn");
const authBtnText = document.getElementById("auth-btn-text");
const authBtnSpinner = document.getElementById("auth-btn-spinner");
const authBanner = document.getElementById("auth-banner");
const authBannerMsg = document.getElementById("auth-banner-message");
const authBannerIcon = document.getElementById("auth-banner-icon");
const authTitle = document.getElementById("auth-title");
const authSubtitle = document.getElementById("auth-subtitle");

const userControls = document.getElementById("user-controls");
const userEmailText = document.getElementById("user-email-text");
const logoutBtn = document.getElementById("logout-btn");

// DOM Elements — Config Modal
const configBtn = document.getElementById("config-btn");
const configModal = document.getElementById("config-modal");
const closeModalBtn = document.getElementById("close-modal-btn");
const cfgUrlInput = document.getElementById("cfg-url");
const cfgKeyInput = document.getElementById("cfg-key");
const saveConfigBtn = document.getElementById("save-config-btn");

/**
 * Initialize Supabase Client & Setup Auth Listeners
 */
async function initSupabase() {
    const savedUrl = localStorage.getItem("docsync_supabase_url") || DEFAULT_SUPABASE_URL;
    const savedKey = localStorage.getItem("docsync_supabase_key") || DEFAULT_SUPABASE_ANON_KEY;

    if (cfgUrlInput) cfgUrlInput.value = savedUrl !== DEFAULT_SUPABASE_URL ? savedUrl : "";
    if (cfgKeyInput) cfgKeyInput.value = savedKey !== DEFAULT_SUPABASE_ANON_KEY ? savedKey : "";

    try {
        if (window.supabase && savedUrl && savedKey) {
            supabaseClient = window.supabase.createClient(savedUrl, savedKey, {
                auth: {
                    persistSession: true,
                    autoRefreshToken: true,
                    detectSessionInUrl: true
                }
            });
            console.log("Supabase client initialized successfully with session persistence.");

            // Check current session
            const { data: { session }, error: sessionError } = await supabaseClient.auth.getSession();
            if (sessionError) {
                console.warn("Session retrieval error:", sessionError);
            }
            updateAuthUI(session?.user || null);

            // Listen for auth state changes
            supabaseClient.auth.onAuthStateChange((event, session) => {
                console.log("Auth state change event:", event);
                updateAuthUI(session?.user || null);
            });
        } else {
            console.warn("Supabase library not loaded or credentials missing.");
        }
    } catch (err) {
        console.error("Failed to initialize Supabase client:", err);
    }
}

/**
 * Update UI depending on whether a user is logged in
 */
function updateAuthUI(user) {
    currentUser = user;

    if (user) {
        // Authenticated State
        if (userEmailText) userEmailText.textContent = user.email || "Authenticated User";
        if (userControls) userControls.classList.remove("hidden");
        if (authCard) authCard.classList.add("hidden");
        if (transferCard) transferCard.classList.remove("hidden");
        if (infoGrid) infoGrid.classList.remove("hidden");
        hideAuthBanner();
    } else {
        // Unauthenticated State
        if (userControls) userControls.classList.add("hidden");
        if (transferCard) transferCard.classList.add("hidden");
        if (infoGrid) infoGrid.classList.add("hidden");
        if (authCard) authCard.classList.remove("hidden");
    }
}

/**
 * Switch Auth Mode (Sign In vs Create Account)
 */
function setAuthMode(mode) {
    authMode = mode;
    hideAuthBanner();

    if (mode === "login") {
        tabLogin.classList.add("active");
        tabLogin.setAttribute("aria-selected", "true");
        tabSignup.classList.remove("active");
        tabSignup.setAttribute("aria-selected", "false");
        authTitle.textContent = "Welcome back to DocSync";
        authSubtitle.textContent = "Sign in to access your secure device pairing and file transfer dashboard.";
        authBtnText.textContent = "Sign In";
    } else {
        tabSignup.classList.add("active");
        tabSignup.setAttribute("aria-selected", "true");
        tabLogin.classList.remove("active");
        tabLogin.setAttribute("aria-selected", "false");
        authTitle.textContent = "Create your DocSync Account";
        authSubtitle.textContent = "Sign up with your email and password to begin transferring documents with Row-Level Security.";
        authBtnText.textContent = "Create Account";
    }
}

tabLogin.addEventListener("click", () => setAuthMode("login"));
tabSignup.addEventListener("click", () => setAuthMode("signup"));

/**
 * Show / Hide Auth Status Banner
 */
function showAuthBanner(message, type = "error") {
    authBanner.className = `banner ${type}`;
    authBannerMsg.textContent = message;

    if (authBannerIcon) {
        authBannerIcon.innerHTML = type === "success"
            ? `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`
            : `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;
    }

    authBanner.classList.remove("hidden");
}

function hideAuthBanner() {
    if (authBanner) authBanner.classList.add("hidden");
}

/**
 * Handle Auth Form Submission (Sign In / Sign Up)
 */
authForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    if (!supabaseClient) {
        showAuthBanner("Supabase client is not configured. Click 'Config' in the header to set your project credentials.", "error");
        return;
    }

    const email = authEmailInput.value.trim();
    const password = authPasswordInput.value;

    if (!email || !password) {
        showAuthBanner("Please provide both email and password.", "error");
        return;
    }

    if (password.length < 6) {
        showAuthBanner("Password must be at least 6 characters.", "error");
        return;
    }

    try {
        isAuthenticating = true;
        authSubmitBtn.disabled = true;
        authBtnSpinner.classList.remove("hidden");
        authBtnText.classList.add("hidden");
        hideAuthBanner();

        if (authMode === "login") {
            const { data, error } = await supabaseClient.auth.signInWithPassword({
                email,
                password
            });

            if (error) throw error;

            showAuthBanner("Signed in successfully!", "success");
            authEmailInput.value = "";
            authPasswordInput.value = "";
            updateAuthUI(data.user);

        } else {
            const { data, error } = await supabaseClient.auth.signUp({
                email,
                password
            });

            if (error) throw error;

            if (data.user && !data.session) {
                // Email confirmation is required by Supabase project settings
                showAuthBanner("Account created! Please check your email inbox to confirm your account.", "success");
            } else {
                showAuthBanner("Account created and signed in!", "success");
                authEmailInput.value = "";
                authPasswordInput.value = "";
                updateAuthUI(data.user);
            }
        }
    } catch (err) {
        console.error("Authentication error:", err);
        showAuthBanner(err.message || "Failed to authenticate. Please check your credentials.", "error");
    } finally {
        isAuthenticating = false;
        authSubmitBtn.disabled = false;
        authBtnSpinner.classList.add("hidden");
        authBtnText.classList.remove("hidden");
    }
});

/**
 * Handle Sign Out
 */
logoutBtn.addEventListener("click", async () => {
    try {
        if (supabaseClient) {
            await supabaseClient.auth.signOut();
        }
        updateAuthUI(null);
        showAuthBanner("You have signed out successfully.", "success");
    } catch (err) {
        console.error("Sign out error:", err);
        updateAuthUI(null);
    }
});

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
 * Return tailored SVG icon based on file extension
 */
function getFileIconSvg(fileName) {
    const ext = fileName.split(".").pop().toLowerCase();
    
    // PDF
    if (ext === "pdf") {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="9" y1="13" x2="15" y2="13"></line>
            <line x1="9" y1="17" x2="13" y2="17"></line>
        </svg>`;
    }
    
    // Images
    if (["jpg", "jpeg", "png", "webp", "svg", "gif", "avif"].includes(ext)) {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
            <circle cx="8.5" cy="8.5" r="1.5"></circle>
            <polyline points="21 15 16 10 5 21"></polyline>
        </svg>`;
    }
    
    // Videos
    if (["mp4", "mkv", "mov", "webm", "avi"].includes(ext)) {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="23 7 16 12 23 17 23 7"></polygon>
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect>
        </svg>`;
    }
    
    // Audio
    if (["mp3", "wav", "flac", "m4a", "ogg", "aac"].includes(ext)) {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18V5l12-2v13"></path>
            <circle cx="6" cy="18" r="3"></circle>
            <circle cx="18" cy="16" r="3"></circle>
        </svg>`;
    }
    
    // Archives & Packages (ZIP, APK, TAR, RAR, 7Z)
    if (["zip", "apk", "rar", "7z", "tar", "gz"].includes(ext)) {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
            <line x1="12" y1="22.08" x2="12" y2="12"></line>
        </svg>`;
    }
    
    // Code & Markup
    if (["html", "css", "js", "ts", "json", "kt", "py", "cpp", "java", "sql", "xml"].includes(ext)) {
        return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="16 18 22 12 16 6"></polyline>
            <polyline points="8 6 2 12 8 18"></polyline>
        </svg>`;
    }

    // Default Document
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
        <polyline points="14 2 14 8 20 8"></polyline>
    </svg>`;
}

/**
 * Show / Hide Transfer Status Banner
 */
function showBanner(message, type = "success") {
    statusBanner.className = `banner ${type}`;
    bannerMessage.textContent = message;
    
    if (bannerIcon) {
        bannerIcon.innerHTML = type === "success" 
            ? `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`
            : `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;
    }
    
    statusBanner.classList.remove("hidden");
}

function hideBanner() {
    if (statusBanner) statusBanner.classList.add("hidden");
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
    if (fileTypeIconEl) {
        fileTypeIconEl.innerHTML = getFileIconSvg(file.name);
    }
    dropZoneEmpty.classList.add("hidden");
    filePreview.classList.remove("hidden");
}

/**
 * Event Listeners: 6-Digit Code Input
 */
codeInput.addEventListener("input", (e) => {
    const cleaned = e.target.value.replace(/\D/g, "").slice(0, 6);
    e.target.value = cleaned;

    const count = cleaned.length;
    const digitCountEl = codeStatus.querySelector(".digit-count");
    if (digitCountEl) {
        digitCountEl.textContent = `${count}/6 digits`;
    }

    if (count === 6) {
        codeStatus.classList.add("valid");
        codeInput.style.borderColor = "var(--accent-emerald)";
    } else {
        codeStatus.classList.remove("valid");
        codeInput.style.borderColor = "";
    }
});

/**
 * Event Listeners: Drag & Drop with Drag Counter Tracking
 */
dropZone.addEventListener("dragenter", (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter++;
    dropZone.classList.add("dragover");
});

dropZone.addEventListener("dragover", (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!dropZone.classList.contains("dragover")) {
        dropZone.classList.add("dragover");
    }
});

dropZone.addEventListener("dragleave", (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter--;
    if (dragCounter <= 0) {
        dragCounter = 0;
        dropZone.classList.remove("dragover");
    }
});

dropZone.addEventListener("drop", (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter = 0;
    dropZone.classList.remove("dragover");

    const dt = e.dataTransfer;
    if (dt && dt.files && dt.files.length > 0) {
        handleFile(dt.files[0]);
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
 * Uploads a file, creates a temporary signed URL, and upserts the session into sync_sessions.
 * @param {Object} supabase - The Supabase client instance
 * @param {string} code - The 6-digit pairing code (session ID)
 * @param {File} file - The file to transfer
 * @param {string} userId - The authenticated user's ID
 * @returns {Promise<{ downloadUrl: string, filePath: string }>}
 */
async function uploadAndBroadcastSyncSession(supabase, code, file, userId) {
    const cleanName = file.name.replace(/[^a-zA-Z0-9._-]/g, "_");
    const filePath = `sync_${code}_${Date.now()}_${cleanName}`;

    // 1. Upload file to Supabase Storage bucket 'sync_uploads'
    const { error: uploadError } = await supabase.storage
        .from(STORAGE_BUCKET)
        .upload(filePath, file, {
            cacheControl: "3600",
            upsert: true
        });

    if (uploadError) {
        throw new Error(`Storage upload failed: ${uploadError.message}`);
    }

    // 2. Generate temporary signed URL (valid for 60 seconds)
    const { data: signedData, error: signedError } = await supabase.storage
        .from(STORAGE_BUCKET)
        .createSignedUrl(filePath, 60);

    if (signedError || !signedData?.signedUrl) {
        throw new Error(`Failed to generate signed download URL: ${signedError?.message || "Unknown error"}`);
    }

    const downloadUrl = signedData.signedUrl;

    // 3. Upsert record into sync_sessions table using 6-digit code as primary key ID
    const { error: upsertError } = await supabase
        .from(TABLE_NAME)
        .upsert([
            {
                id: code,
                download_url: downloadUrl,
                file_name: file.name,
                file_size: file.size,
                user_id: userId
            }
        ]);

    if (upsertError) {
        throw new Error(`Database upsert failed: ${upsertError.message}`);
    }

    return { downloadUrl, filePath };
}

/**
 * Form Submission: Authenticated Upload to Supabase Storage & Upsert into sync_sessions
 */
syncForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    // Verify authenticated user
    const { data: { user } } = await supabaseClient.auth.getUser();
    if (!user) {
        showBanner("You must be logged in to transfer files.", "error");
        updateAuthUI(null);
        return;
    }

    const code = codeInput.value.trim();
    if (code.length !== 6) {
        showBanner("Please enter a valid 6-digit sync pairing code.", "error");
        codeInput.focus();
        return;
    }

    if (!selectedFile) {
        showBanner("Please select a file or document to transfer.", "error");
        return;
    }

    try {
        isUploading = true;
        submitBtn.disabled = true;
        btnSpinner.classList.remove("hidden");
        submitBtn.querySelector(".btn-text").classList.add("hidden");
        hideBanner();

        updateProgress(30, "Uploading file securely to Supabase Storage...");
        
        // Execute upload, signed URL generation, and upsert
        await uploadAndBroadcastSyncSession(supabaseClient, code, selectedFile, user.id);

        // Complete
        updateProgress(100, "Transferred successfully!");
        showBanner(
            `🚀 "${selectedFile.name}" successfully synced to paired Android device (${code})!`,
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
        }, 3500);
    }
});

/**
 * Config Modal Event Listeners
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
