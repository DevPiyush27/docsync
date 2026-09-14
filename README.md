<div align="center">

# 🔄 DocSync

**Seamless, Secure, and Instant File Transfers between Web and Android.**

[![Website](https://img.shields.io/badge/Web_Portal-Live-00E676?style=for-the-badge&logo=google-chrome&logoColor=white)](https://devpiyush27.github.io/docsync/)
[![Download APK](https://img.shields.io/badge/Download_APK-v1.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/DevPiyush27/docsync/releases/download/v.1.0.3/app-debug.apk)

*No cables. No complex logins. Just a 6-digit code and a custom monochrome brand identity.*

</div>

---

## ✨ Overview

**DocSync** is a cross-platform file synchronization tool designed to make transferring documents between your computer and your Android device as frictionless as possible. Featuring custom-tailored branding across clients, it utilizes a robust cloud backend to ensure your files are securely isolated and transferred in real-time.

Simply generate a 6-digit pairing code on your Android device, enter it into the web portal, and watch your files sync instantly to your device's public Downloads directory.

---

## 🚀 Key Features

* **⚡ Instant P2P-Style Sync:** Powered by real-time database subscriptions and WebSocket channels for zero-delay transfers.
* **🔒 Secure by Design:** Utilizes PostgreSQL Row-Level Security (RLS) and Temporary Signed URLs so files are accessible *only* via authorized sessions.
* **🔑 Passwordless Auth:** Fast, secure authentication via email OTP.
* **📱 Native Android Experience:** Built from the ground up with Kotlin, Jetpack Compose, and system `DownloadManager` integration for seamless background file handling.
* **🌐 Lightweight Web Portal:** A fast, responsive HTML/CSS/JS web interface hosted cleanly on GitHub Pages.
* **🎨 Custom Branding:** Features a unified, sleek dark-themed aesthetic with custom-designed monochrome app icons and matching web favicons.

---

## 🛠️ Tech Stack

### **Frontend (Web)**
* HTML5 / CSS3 / JavaScript
* Hosted on GitHub Pages (`devpiyush27.github.io/docsync/`)

### **Mobile (Android)**
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose
* **System Integration:** Android `DownloadManager` for automated public directory file saves and notification support

### **Backend & Infrastructure (Supabase)**
* **Database:** PostgreSQL (secured with Row-Level Security)
* **Storage:** Supabase Buckets (managed via temporary Signed URLs)
* **Auth:** Supabase Auth (OTP via Resend SMTP)
* **Realtime:** Supabase Realtime Channels for instant event broadcasting

---

## 💡 How It Works (Architecture & Workflow)

1. **Authenticate:** Log in securely via email OTP on the web portal.
2. **Generate Code:** Open the DocSync Android app to request a unique, time-sensitive 6-digit pairing code linked to your active session.
3. **Transfer:** Enter the 6-digit code on the web portal, select your file (up to 50MB), and hit Send. The file uploads directly to Supabase storage buckets.
4. **Broadcast:** A Realtime event broadcasts the payload metadata to your authenticated Android client.
5. **Download:** The Android app intercepts the event, fetches the secure temporary signed URL, and triggers the native `DownloadManager` to save the file straight to your device's **Downloads** folder with complete notification tracking.

---

## 📥 Installation & Usage

### **For Android:**
1. Download the latest `.apk` from the [Releases](https://github.com/DevPiyush27/docsync/releases/download/v.1.0.3/app-debug.apk) section.
2. Install the application on your Android device (ensure installation from unknown sources is permitted if prompted).
3. Open the app to generate your active sync code.

### **For Desktop / Web:**
1. Navigate to the live [DocSync Web Portal](https://devpiyush27.github.io/docsync/).
2. Enter your email to receive a secure passwordless login link.
3. Enter the 6-digit code displayed on your phone, upload your document, and enjoy instant wireless transfer!

---

<div align="center">
  <i>Built with ❤️ to make file sharing simple, secure, and cable-free.</i>
</div>
