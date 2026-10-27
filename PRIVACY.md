# Privacy Policy — Cloud Set Storage

**Last Updated:** October 2026  
**Developer:** Made by praveen  
**Contact:** praveensahani9273@gmail.com

---

## 1. Principles of Data Privacy

Cloud Set Storage is built with security and data autonomy as core priorities:
- **Local First:** Your camera photos and videos are stored directly in your device's private storage before any backup action takes place.
- **No Automatic Deletions:** Cloud Set Storage never deletes your local media without explicit, itemized user consent.
- **Direct Cloud Communication:** All authentication and file transfers communicate directly with official Google APIs (`accounts.google.com` and `www.googleapis.com`) through secure HTTPS channels. No intermediate proxy servers or third-party servers receive your credentials or media.

---

## 2. Google Drive Permissions (`drive.file`)

Cloud Set Storage requests the narrowest possible permission scope:
- **Scope Requested:** `https://www.googleapis.com/auth/drive.file`
- **What this allows:** The application can only view, upload, modify, or delete files that were created by or explicitly opened with Cloud Set Storage.
- **What this prevents:** The application cannot read, search, index, or access any other files, folders, spreadsheets, or documents in your Google Drive.

---

## 3. On-Device Storage & Diagnostics

- **Room Database:** Retains file names, local file URIs, file sizes, capture dates, and upload progress to maintain the backup queue across reboots.
- **Credentials:** Authentication tokens are managed exclusively through Google Play Services and Android Credential Manager. No passwords or private tokens are stored in plain text or shared.
- **Account Disconnection:** Disconnecting a Google account removes the local session token and active account assignment. Files already uploaded to Google Drive remain untouched in your cloud account.

---

## 4. Hardware Permissions

- `CAMERA`: Used exclusively to provide the live camera preview and take photos or record videos at user request.
- `RECORD_AUDIO`: Used exclusively for capturing video sound when enabled by the user. If denied, the app continues to record video without audio.
- `POST_NOTIFICATIONS`: Used only to display upload progress in the Android status bar for foreground WorkManager tasks.
- `INTERNET` & `ACCESS_NETWORK_STATE`: Used to verify network connectivity (such as detecting unmetered Wi-Fi) and perform encrypted data sync with Google Drive.

---

## 5. Contact & Inquiries

For questions or security disclosures, please reach out to:  
**Praveen** — praveensahani9273@gmail.com
