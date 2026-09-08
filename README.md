<div align="center">

# 🔄 DocSync

**Seamless, Secure, and Instant File Transfers between Web and Android.**

[![Website](https://img.shields.io/badge/Web_Portal-Live-00E676?style=for-the-badge&logo=google-chrome&logoColor=white)](YOUR_WEBSITE_LINK_HERE)
[![Download APK](https://img.shields.io/badge/Download_APK-v1.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](YOUR_APK_LINK_HERE)

*No cables. No complex logins. Just a 6-digit code.*

</div>

---

## ✨ Overview

**DocSync** is a cross-platform file synchronization tool designed to make transferring documents between your computer and your Android device as frictionless as possible. Built with a robust backend, it ensures your files are securely isolated and transferred in real-time.

Simply generate a 6-digit pairing code on your Android device, enter it into the web portal, and watch your files sync instantly.

## 🚀 Key Features

*   **⚡ Instant P2P-Style Sync:** Powered by real-time database subscriptions for zero-delay transfers.
*   **🔒 Secure by Design:** Utilizes Row-Level Security (RLS) and Temporary Signed URLs to ensure your files are accessible *only* by you.
*   **🔑 Passwordless Auth:** Fast, secure email OTP login.
*   **📱 Native Android Experience:** Built from the ground up with Jetpack Compose for a fluid, modern UI.
*   **🌐 Lightweight Web Portal:** A fast, responsive web interface for uploading files from any desktop browser.

## 🛠️ Tech Stack

**Frontend (Web)**
*   HTML5 / CSS3 / JavaScript
*   Hosted on GitHub Pages

**Mobile (Android)**
*   Kotlin
*   Jetpack Compose
*   Android `DownloadManager`

**Backend (Supabase)**
*   **Database:** PostgreSQL (with Row-Level Security)
*   **Storage:** Supabase Buckets (Signed URLs)
*   **Auth:** Supabase Auth (OTP via Resend SMTP)
*   **Realtime:** Supabase Realtime Channels

---

## 📸 Screenshots

*(Add screenshots of your project here to make the README pop!)*

<div align="center">
  <img src="LINK_TO_YOUR_WEB_SCREENSHOT.png" alt="Web Portal" width="45%">
  &nbsp; &nbsp; &nbsp;
  <img src="LINK_TO_YOUR_MOBILE_SCREENSHOT.jpg" alt="Android App" width="22%">
</div>

---

## 💡 How It Works

1.  **Authenticate:** Log in securely via email OTP on the web portal.
2.  **Generate Code:** Open the DocSync Android app to generate a unique, one-time 6-digit pairing code.
3.  **Transfer:** Enter the code on the web portal, select your file (up to 50MB), and hit Send.
4.  **Download:** The Android app instantly receives a Realtime broadcast, decrypts the signed URL, and saves the file straight to your device's Downloads folder.

---

## 📥 Installation & Usage

**For Android:**
1. Download the latest `.apk` from the [Releases](YOUR_APK_LINK_HERE) section.
2. Install the application on your Android device.
3. Open the app to generate your sync code.

**For Desktop/Web:**
1. Navigate to the [DocSync Web Portal](YOUR_WEBSITE_LINK_HERE).
2. Enter your email to receive a secure login link.
3. Enter the 6-digit code from your phone and upload your file.

---

<div align="center">
  <i>Built with ❤️ to make file sharing simple.</i>
</div>
