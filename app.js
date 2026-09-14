/**
 * ==============================================================================
 * DocSync — Web Application Controller (Supabase JS v2)
 * Original Network Core + New Sliding Auth UI Integration
 * ==============================================================================
 */

// 1. Default Supabase Credentials
const DEFAULT_SUPABASE_URL = "https://zqiozemfwidrlpksxbza.supabase.co";
const DEFAULT_SUPABASE_ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpxaW96ZW1md2lkcmxwa3N4YnphIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3ODQ1MzUsImV4cCI6MjEwNDM2MDUzNX0.p50TkFOysj6nKy1xbaQWwuV-SirkAhdL_EzqGs_rcZk";

// Maximum file size: 50 Megabytes in bytes
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 52,428,800 bytes
const STORAGE_BUCKET = "sync_uploads";
const TABLE_NAME = "sync_sessions";

// State
let supabaseClient = null;
let currentUser = null;
let selectedFile = null;
let isUploading = false;
let dragCounter = 0;  

// DOM Elements — Main Card & Transfer
const infoGrid = document.getElementById("info-grid");
const transferCard = document.getElementById("transfer-card");
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

// DOM Elements — Sliding Auth Card
const authCard = document.getElementById("auth-card");
const containerEl = document.getElementById("container");
const loginToggle = document.getElementById("login");
const registerToggle = document.getElementById("register");

const signinForm = document.getElementById("signin-form");
const signinEmailInput = document.getElementById("signin-email");
const signinPasswordInput = document.getElementById("signin-password");
const signinSubmitBtn = document.getElementById("signin-submit-btn");
const signinSpinner = document.getElementById("signin-spinner");
const signinBanner = document.getElementById("signin-banner");
const signinBannerMsg = document.getElementById("signin-banner-message");

const signupForm = document.getElementById("signup-form");
const signupEmailInput = document.getElementById("signup-email");
const signupPasswordInput = document.getElementById("signup-password");
const signupSubmitBtn = document.getElementById("signup-submit-btn");
const signupSpinner = document.getElementById("signup-spinner");
const signupBanner = document.getElementById("signup-banner");
const signupBannerMsg = document.getElementById("signup-banner-message");

const userControls = document.getElementById("user-controls");
const userEmailText = document.getElementById("user-email-text");
const logoutBtn = document.getElementById("logout-btn");

// DOM Elements — Config Modal & Theme
const configBtn = document.getElementById("config-btn");
const configModal = document.getElementById("config-modal");
const closeModalBtn = document.getElementById("close-modal-btn");
const cfgUrlInput = document.getElementById("cfg-url");
const cfgKeyInput = document.getElementById("cfg-key");
const saveConfigBtn = document.getElementById("save-config-btn");

const themeToggleBtn = document.getElementById("theme-toggle-btn");
const moonIcon = themeToggleBtn.querySelector(".moon-icon");
const sunIcon = themeToggleBtn.querySelector(".sun-icon");

/**
 * Handle Sliding Authentication Panels
 */
registerToggle.addEventListener("click", () => {
  containerEl.classList.add("active");
});

loginToggle.addEventListener("click", () => {
  containerEl.classList.remove("active");
});

/**
 * Initialize Supabase Client & Setup Auth Listeners
 */
async function initSupabase() {
  const savedUrl =
    localStorage.getItem("docsync_supabase_url") || DEFAULT_SUPABASE_URL;
  const savedKey =
    localStorage.getItem("docsync_supabase_key") || DEFAULT_SUPABASE_ANON_KEY;

  if (cfgUrlInput)
    cfgUrlInput.value = savedUrl !== DEFAULT_SUPABASE_URL ? savedUrl : "";
  if (cfgKeyInput)
    cfgKeyInput.value = savedKey !== DEFAULT_SUPABASE_ANON_KEY ? savedKey : "";

  try {
    if (window.supabase && savedUrl && savedKey) {
      supabaseClient = window.supabase.createClient(savedUrl, savedKey, {
        auth: {
          persistSession: true,
          autoRefreshToken: true,
          detectSessionInUrl: true,
        },
      });
      console.log("Supabase client initialized successfully.");

      const {
        data: { session },
        error: sessionError,
      } = await supabaseClient.auth.getSession();
      
      if (sessionError) {
        console.warn("Session retrieval error:", sessionError);
      }
      
      updateAuthUI(session?.user || null);

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
    if (userEmailText) userEmailText.textContent = user.email || "Authenticated User";
    if (userControls) userControls.classList.remove("hidden");
    if (authCard) authCard.classList.add("hidden");
    if (transferCard) transferCard.classList.remove("hidden");
    if (infoGrid) infoGrid.classList.remove("hidden"); // <-- ADD THIS LINE HERE
  } else {
    if (userControls) userControls.classList.add("hidden");
    if (transferCard) transferCard.classList.add("hidden");
    if (authCard) authCard.classList.remove("hidden");
    if (infoGrid) infoGrid.classList.add("hidden"); // <-- AND ADD THIS LINE HERE
  }
}

function showFormBanner(bannerEl, msgEl, message, type = "error") {
  bannerEl.className = `banner ${type}`;
  msgEl.textContent = message;
  bannerEl.classList.remove("hidden");
}

function hideFormBanner(bannerEl) {
  if (bannerEl) bannerEl.classList.add("hidden");
}

/**
 * Handle Sign In Submission
 */
signinForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!supabaseClient) {
    return showFormBanner(signinBanner, signinBannerMsg, "Supabase client not configured.", "error");
  }

  try {
    signinSubmitBtn.disabled = true;
    signinSpinner.classList.remove("hidden");
    hideFormBanner(signinBanner);

    const { data, error } = await supabaseClient.auth.signInWithPassword({
      email: signinEmailInput.value.trim(),
      password: signinPasswordInput.value,
    });

    if (error) throw error;
    
    signinEmailInput.value = "";
    signinPasswordInput.value = "";
    updateAuthUI(data.user);
  } catch (err) {
    showFormBanner(signinBanner, signinBannerMsg, err.message || "Failed to authenticate.", "error");
  } finally {
    signinSubmitBtn.disabled = false;
    signinSpinner.classList.add("hidden");
  }
});

/**
 * Handle Sign Up Submission
 */
signupForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!supabaseClient) {
    return showFormBanner(signupBanner, signupBannerMsg, "Supabase client not configured.", "error");
  }

  try {
    signupSubmitBtn.disabled = true;
    signupSpinner.classList.remove("hidden");
    hideFormBanner(signupBanner);

    const { data, error } = await supabaseClient.auth.signUp({
      email: signupEmailInput.value.trim(),
      password: signupPasswordInput.value,
    });

    if (error) throw error;

    if (data.user && !data.session) {
      showFormBanner(signupBanner, signupBannerMsg, "Account created! Please check your email to confirm.", "success");
    } else {
      signupEmailInput.value = "";
      signupPasswordInput.value = "";
      updateAuthUI(data.user);
    }
  } catch (err) {
    showFormBanner(signupBanner, signupBannerMsg, err.message || "Failed to create account.", "error");
  } finally {
    signupSubmitBtn.disabled = false;
    signupSpinner.classList.add("hidden");
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
 * Uploads a file with real-time byte progress reporting via XMLHttpRequest.
 */
async function uploadWithRealProgress(file, storagePath, token) {
  const currentUrl = localStorage.getItem("docsync_supabase_url") || DEFAULT_SUPABASE_URL;
  const currentKey = localStorage.getItem("docsync_supabase_key") || DEFAULT_SUPABASE_ANON_KEY;

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${currentUrl}/storage/v1/object/${STORAGE_BUCKET}/${storagePath}`);
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.setRequestHeader("apikey", currentKey);
    xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");
    xhr.setRequestHeader("x-upsert", "true");

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const percent = (e.loaded / e.total) * 100;
        updateProgress(percent, "Uploading... (" + formatBytes(e.loaded) + " / " + formatBytes(e.total) + ")");
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response);
      } else {
        let errorMessage = `Storage upload failed with status ${xhr.status}`;
        try {
          const parsed = JSON.parse(xhr.responseText);
          if (parsed.message || parsed.error) errorMessage = parsed.message || parsed.error;
        } catch (_) {}
        reject(new Error(errorMessage));
      }
    };

    xhr.onerror = () => reject(new Error("Network error occurred during file upload."));
    xhr.send(file);
  });
}

function showBanner(message, type = "success") {
  statusBanner.className = `banner ${type}`;
  bannerMessage.textContent = message;
  statusBanner.classList.remove("hidden");
}

function hideBanner() {
  if (statusBanner) statusBanner.classList.add("hidden");
}

function updateProgress(percent, message) {
  progressSection.classList.remove("hidden");
  progressBar.style.width = `${percent}%`;
  progressPercent.textContent = `${Math.round(percent)}%`;
  if (message) progressStatus.textContent = message;
}

function handleFile(file) {
  hideBanner();
  if (!file) {
    selectedFile = null;
    dropZoneEmpty.classList.remove("hidden");
    filePreview.classList.add("hidden");
    return;
  }
  if (file.size > MAX_FILE_SIZE_BYTES) {
    showBanner(`File is too large (${formatBytes(file.size)}). Maximum allowed size is 50MB.`, "error");
    fileInput.value = "";
    selectedFile = null;
    return;
  }
  selectedFile = file;
  fileNameEl.textContent = file.name;
  fileSizeEl.textContent = formatBytes(file.size);
  dropZoneEmpty.classList.add("hidden");
  filePreview.classList.remove("hidden");
}

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
  if (e.dataTransfer && e.dataTransfer.files.length > 0) handleFile(e.dataTransfer.files[0]);
});

fileInput.addEventListener("change", (e) => {
  if (e.target.files && e.target.files.length > 0) handleFile(e.target.files[0]);
});

removeFileBtn.addEventListener("click", (e) => {
  e.preventDefault();
  e.stopPropagation();
  fileInput.value = "";
  handleFile(null);
});

async function uploadAndBroadcastSyncSession(supabase, code, file, userId) {
  const cleanName = file.name.replace(/[^a-zA-Z0-9._-]/g, "_");
  const filePath = `sync_${code}_${Date.now()}_${cleanName}`;
  const { data: { session } } = await supabase.auth.getSession();
  const token = session ? session.access_token : "";

  await uploadWithRealProgress(file, filePath, token);

  const { data: signedData, error: signedError } = await supabase.storage
    .from(STORAGE_BUCKET)
    .createSignedUrl(filePath, 60);

  if (signedError || !signedData?.signedUrl) {
    throw new Error(`Failed to generate signed download URL: ${signedError?.message || "Unknown error"}`);
  }

  const downloadUrl = signedData.signedUrl;

  const { error: upsertError } = await supabase.from(TABLE_NAME).upsert([
    {
      id: code,
      download_url: downloadUrl,
      file_name: file.name,
      file_size: file.size,
      user_id: userId,
    },
  ]);

  if (upsertError) throw new Error(`Database upsert failed: ${upsertError.message}`);

  return { downloadUrl, filePath };
}

syncForm.addEventListener("submit", async (e) => {
  e.preventDefault();
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

    updateProgress(0, "Starting secure upload...");

    await uploadAndBroadcastSyncSession(supabaseClient, code, selectedFile, user.id);

    updateProgress(100, "Transferred successfully!");
    showBanner(`🚀 "${selectedFile.name}" successfully synced to paired Android device (${code})!`, "success");
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
      if (!isUploading) progressSection.classList.add("hidden");
    }, 3500);
  }
});

// ==============================================================================
// DocSync — Offline Queue: "Send Later" Functionality
// ==============================================================================

async function uploadToOfflineQueue() {
  console.log("==================================================");
  console.log("🚀 [DocSync Queue] 'Send Later' clicked. Starting workflow...");

  if (!selectedFile) {
    showBanner("Please select a document or file first to queue for offline sync.", "error");
    return;
  }

  if (!supabaseClient) {
    showBanner("Supabase client is not connected. Please check your config.", "error");
    return;
  }

  const sendLaterBtn = document.getElementById("send-later-btn");
  const sendLaterSpinner = document.getElementById("send-later-spinner");
  const sendLaterText = sendLaterBtn ? sendLaterBtn.querySelector(".btn-text") : null;

  try {
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser();

    if (authError || !user) {
      showBanner("You must be logged in to queue files for offline sync.", "error");
      updateAuthUI(null);
      return;
    }

    if (sendLaterBtn) sendLaterBtn.disabled = true;
    if (submitBtn) submitBtn.disabled = true;
    if (sendLaterSpinner) sendLaterSpinner.classList.remove("hidden");
    if (sendLaterText) sendLaterText.classList.add("hidden");
    hideBanner();

    updateProgress(0, "Starting upload to storage bucket...");

    const sanitizedFileName = selectedFile.name.replace(/[^a-zA-Z0-9._-]/g, "_");
    const storagePath = `queue/${user.id}/${Date.now()}_${sanitizedFileName}`;

    const { data: { session } } = await supabaseClient.auth.getSession();
    const token = session ? session.access_token : "";

    await uploadWithRealProgress(selectedFile, storagePath, token);
    updateProgress(65, "Registering entry in 'sync_queue' database table...");

    const queuePayload = {
      user_id: user.id,
      file_name: selectedFile.name,
      file_path: storagePath,
      file_size: selectedFile.size,
      status: "pending",
    };

    const { data: insertData, error: insertError } = await supabaseClient
      .from("sync_queue")
      .insert([queuePayload])
      .select();

    if (insertError) {
      await supabaseClient.storage.from("sync_uploads").remove([storagePath]);
      throw new Error(`Database error saving to queue: ${insertError.message}`);
    }

    updateProgress(100, "Queued successfully!");
    showBanner(`📦 "${selectedFile.name}" added to Offline Queue! Your Android device will automatically download it when connected.`, "success");
    fileInput.value = "";
    handleFile(null);
  } catch (err) {
    console.error("💥 [DocSync Queue] Fatal exception during queue operation:", err);
    showBanner(err.message || "An unexpected error occurred while queueing.", "error");
  } finally {
    if (sendLaterBtn) sendLaterBtn.disabled = false;
    if (submitBtn) submitBtn.disabled = false;
    if (sendLaterSpinner) sendLaterSpinner.classList.add("hidden");
    if (sendLaterText) sendLaterText.classList.remove("hidden");

    console.log("🏁 [DocSync Queue] Workflow finished.");
    console.log("==================================================");

    setTimeout(() => {
      if (!isUploading) progressSection.classList.add("hidden");
    }, 3500);
  }
}

function wireSendLaterButton() {
  const sendLaterBtn = document.getElementById("send-later-btn");
  if (sendLaterBtn) {
    sendLaterBtn.removeEventListener("click", uploadToOfflineQueue);
    sendLaterBtn.addEventListener("click", uploadToOfflineQueue);
  }
}

wireSendLaterButton();
document.addEventListener("DOMContentLoaded", wireSendLaterButton);

/**
 * Theme Toggle & Config
 */
function setTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem("docsync_theme", theme);
  if (theme === "dark") {
    moonIcon.classList.add("hidden");
    sunIcon.classList.remove("hidden");
  } else {
    sunIcon.classList.add("hidden");
    moonIcon.classList.remove("hidden");
  }
}

const savedTheme = localStorage.getItem("docsync_theme") || "light";
setTheme(savedTheme);

themeToggleBtn.addEventListener("click", () => {
  const currentTheme = document.documentElement.getAttribute("data-theme");
  setTheme(currentTheme === "dark" ? "light" : "dark");
});

configBtn.addEventListener("click", () => configModal.classList.remove("hidden"));
closeModalBtn.addEventListener("click", () => configModal.classList.add("hidden"));
configModal.addEventListener("click", (e) => {
  if (e.target === configModal) configModal.classList.add("hidden");
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

window.addEventListener("DOMContentLoaded", () => {
  initSupabase();
});