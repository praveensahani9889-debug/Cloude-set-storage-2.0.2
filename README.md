# Cloud Set Storage

**Production-Minded Android Camera + Google Drive Auto-Backup App**

- **App Name:** Cloud Set Storage
- **Package / Application ID:** `com.cloude.set.storage.praveen`
- **Developer:** Made by praveen
- **Support Email:** praveensahani9273@gmail.com
- **Minimum Android Version:** Android 8.0 (API 26)
- **Target Android Version:** Android 16 (API 36)
- **Language & Framework:** 100% Kotlin with Jetpack Compose & Material 3

---

## 1. Overview & Architecture

Cloud Set Storage is a native Android application combining a CameraX photography and video recording engine with an automatic, resilient Google Drive cloud backup pipeline.

```
[ Camera Capture / User Media Import ]
               │
               ▼
[ Local File Saved on Device Disk ]
               │
               ▼
[ Room Database Record Created (LOCAL_SAVED) ]
               │
               ▼
[ WorkManager Scheduled (QUEUED) ]
        ├── Network Constraints (Wi-Fi Only or Mobile Data)
        └── Charging Constraints
               │
               ▼
[ Google Drive API v3 Resumable Upload (UPLOADING) ]
        ├── Token Acquisition (GoogleAuthManager + drive.file scope)
        └── Chunked Streaming with Progress Counting
               │
               ▼
[ Server Confirmation: Server-Returned ID Verified ]
               │
               ▼
[ Room State Updated to UPLOADED + Quota Refreshed ]
```

### Explicit State Machine

| State | Description |
|---|---|
| `LOCAL_SAVED` | Media safely written to disk; metadata registered in Room. |
| `QUEUED` | Scheduled with WorkManager adhering to network & charging constraints. |
| `UPLOADING` | Actively streaming byte chunks to Google Drive with live % progress. |
| `UPLOADED` | Confirmed by Google Drive v3 server with remote file ID. |
| `RETRY_WAIT` | Transient network error; queued for exponential backoff retry. |
| `FAILED` | Permanent error (e.g. disk corruption, Drive quota full). |
| `NEEDS_AUTH` | Google OAuth token expired or permissions revoked; user action needed. |
| `CANCELED` | Upload stopped by user request. |

---

## 2. Google Cloud Configuration Guide

To enable Google Drive authorization on a real device or emulator:

### Step 1: Create or Select a Google Cloud Project
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Select your project (e.g., `cloud-set-storage`).

### Step 2: Enable Google Drive API v3
1. Navigate to **APIs & Services > Library**.
2. Search for **Google Drive API**.
3. Click **Enable**.

### Step 3: Configure the OAuth Consent Screen
1. Go to **APIs & Services > OAuth consent screen**.
2. Select **External** (or Internal for Google Workspace).
3. Fill in App Name (`Cloud Set Storage`), User Support Email (`praveensahani9273@gmail.com`), and Developer Contact Email.
4. Under **Scopes**, click **Add or Remove Scopes** and add:
   - `.../auth/drive.file` (Per-file access created or opened by this app).
5. Under **Test Users**, add the Google email addresses you will use for testing.

### Step 4: Configure the Android OAuth Client ID
1. Navigate to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
2. Application type: **Android**.
3. Name: `Cloud Set Storage Android Client`.
4. Package name: `com.cloude.set.storage.praveen`.
5. SHA-1 Certificate Fingerprint:
   - For **Debug builds**:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - For **Release builds**:
     Generate from your production keystore:
     ```bash
     keytool -list -v -keystore your-release-key.jks -alias your-alias
     ```
6. Click **Create**.

---

## 3. Features & Screens

1. **Dashboard:** Live Google account identity, Google Drive storage quota bar (used vs limit in GB/MB), backup counters (Waiting, Uploading, Uploaded, Failed), last backup timestamp, Cloud-First mode status banner, and quick navigation.
2. **Camera:** Full CameraX preview, Photo and Video capture modes, front/back lens switching, flash modes (Auto, On, Off, Torch), pinch-to-zoom and zoom presets, tap-to-focus, Rule of Thirds grid overlay, countdown timer (Off, 3s, 10s), recent thumbnail preview, hardware extension detection (HDR, Night, Bokeh), and low-storage warning indicator.
3. **Gallery:** Media grid with status indicators, filtering by type/state, multi-selection, manual upload, deletion, "Open in Drive" action for uploaded files, and zero-permission Android Photo Picker import (`PickVisualMedia`).
4. **Upload Center:** Live queue management, byte-level progress reporting, detailed failure reasons in plain language, individual retry/cancel actions, and a "Retry All" button.
5. **Account Manager:** Manage authorized accounts, switch active destination account, view individual quota allocations, and safely disconnect credentials without touching remote cloud files.
6. **Low-Storage Manager:** Real-time breakdown of internal device storage and app staging storage, configurable staging storage limit (250MB, 500MB, 1GB), list of protected pending files (with plain-language reasons why they cannot yet be removed), and one-tap "Reclaim Device Storage" action for verified Google Drive files.
7. **Settings:** Operating mode switcher (Cloud-First Mode vs. Local + Cloud Mode), automatic cleanup of verified temporary files, Wi-Fi only backup, mobile data upload with explicit user consent, upload while charging, photo/video inclusion, notifications, and cache cleanup.

---

## 4. Cloud-First Architecture & Low-Storage Safety

Cloud Set Storage is engineered specifically for phones with constrained internal storage:

### A. Cloud-First Mode (Recommended)
- Captured photos and videos are written directly to temporary app-private staging storage.
- WorkManager schedules upload to the user's authorized Google Drive account immediately when network conditions allow.
- **Safety Invariant:** Local staging copies are **NEVER** deleted before the Google Drive API v3 server confirms the upload with a unique remote `fileId`.
- Once verified, the staging copy is automatically cleaned up (or manually reclaimed in the Low-Storage Manager), freeing phone storage while preserving the in-app history and Google Drive link.

### B. Local + Cloud Mode
- Keeps the original local media file permanently on the device disk while concurrently uploading a backup copy to Google Drive.

### Offline & Low-Storage Limitations
- **Offline Captures:** Without an internet connection, media remains safely stored in local staging. When internet access (Wi-Fi or permitted mobile data) resumes, WorkManager automatically drains the upload queue.
- **Staging Space Requirement:** A network camera cannot transmit directly to the cloud without local buffering. If the device has less than 100MB of free space, the app warns the user and prompts them to reclaim space or free phone storage.
- **Background Execution Limits:** Android Doze mode and manufacturer power management may defer background uploads until the device is connected to power or unlocked. Foreground service notifications are utilized to ensure resilient long transfers.

---

## 5. Build & CI/CD Pipeline

### Local Build & Unit Tests
```bash
# Run unit tests & Robolectric tests
gradle :app:testDebugUnitTest

# Build Debug APK
gradle :app:assembleDebug
```
The generated APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions CI (`.github/workflows/android.yml`)
The included workflow runs on every push and pull request, as well as manual triggers (`workflow_dispatch`), running unit tests, lint checks, and publishing the Debug APK as an downloadable artifact.

---

## 6. Troubleshooting Common Issues

1. **"Google Sign-In canceled or failed"**:
   - Ensure the SHA-1 fingerprint of your debug keystore (`debug.keystore`) is registered in Google Cloud Console under the Android OAuth Client ID for package `com.cloude.set.storage.praveen`.
2. **"Google Drive storage quota exceeded (HTTP 403)"**:
   - The user's Google Drive account is full. The app pauses uploads, marks tasks as failed with a clear quota alert, and preserves local staging copies so no media is lost.
3. **"Access token expired or revoked (HTTP 401)"**:
   - The task transitions to `NEEDS_AUTH`. The user can re-authenticate or switch accounts in the Account Manager.
4. **"Network error / SocketTimeoutException"**:
   - Tasks enter `RETRY_WAIT` state with exponential backoff (15s, 30s, 60s) until connectivity stabilizes.

---

## 7. Manual Test Checklist

- [ ] **Fresh Installation:** App launches cleanly, displaying "Cloud Set Storage" with deep navy theme and no hardcoded sample data.
- [ ] **Camera Permissions:** Camera prompt appears upon opening the Camera screen; preview renders smoothly.
- [ ] **Photo Capture:** Snapping a photo creates a valid local file and records `LOCAL_SAVED` in Room.
- [ ] **Video Capture:** Recording saves an MP4 file with timer indicator. Audio permission prompt appears gracefully.
- [ ] **Grid & Timer:** Rule of Thirds grid toggles cleanly; 3s and 10s countdown timers animate on screen before capture.
- [ ] **Photo Picker Import:** Tap '+' in Gallery to import an existing photo or video without broad storage permissions.
- [ ] **Google Sign-In:** Authenticate with a Google account to authorize `drive.file` scope.
- [ ] **Upload Flow:** Captured media progresses: `LOCAL_SAVED` -> `QUEUED` -> `UPLOADING` -> `UPLOADED`.
- [ ] **Verified Cleanup:** In Cloud-First Mode, local staging copy is deleted ONLY after server confirmation, freeing phone disk space.
- [ ] **Low-Storage Manager:** Displays real internal free storage, staging storage, non-cleanable protected items, and allows one-tap cleanup of verified files.
- [ ] **Open in Drive:** Uploaded files in Gallery open directly in Google Drive app/browser with remote file ID.
- [ ] **Network Loss Handling:** Disconnecting network transitions active upload to `RETRY_WAIT` with backoff.
- [ ] **Full Storage Quota Warning:** If account quota exceeds 90%, an amber/red warning banner alerts the user.
- [ ] **Account Switching:** Switching active accounts preserves existing upload queue ownership and isolates personal files.
- [ ] **Reboot Recovery:** WorkManager constraints persist pending tasks across app restarts.
