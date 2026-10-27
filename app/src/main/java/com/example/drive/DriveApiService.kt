package com.example.drive

import com.example.drive.model.DriveFileResult
import com.example.drive.model.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DriveApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    suspend fun getStorageQuota(accessToken: String): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota,user")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        DriveApiException(response.code, "Failed to get quota: ${response.code} $body")
                    )
                }

                val json = JSONObject(body)
                val quotaObj = json.optJSONObject("storageQuota")
                val userObj = json.optJSONObject("user")

                val limit = quotaObj?.optLong("limit", -1L) ?: -1L
                val usage = quotaObj?.optLong("usage", 0L) ?: 0L
                val usageInDrive = quotaObj?.optLong("usageInDrive", 0L) ?: 0L
                val usageInTrash = quotaObj?.optLong("usageInDriveTrash", 0L) ?: 0L

                val email = userObj?.optString("emailAddress").orEmpty()
                val displayName = userObj?.optString("displayName")
                val photoUrl = userObj?.optString("photoLink")

                Result.success(
                    QuotaInfo(
                        limitBytes = limit,
                        usageBytes = usage,
                        usageInDriveBytes = usageInDrive,
                        usageInTrashBytes = usageInTrash,
                        userEmail = email,
                        userDisplayName = displayName,
                        userPhotoUrl = photoUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findExistingFile(
        accessToken: String,
        fileName: String,
        folderId: String? = null
    ): Result<DriveFileResult?> = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = fileName.replace("'", "\\'")
            val queryParts = mutableListOf("name = '$sanitizedName'", "trashed = false")
            if (!folderId.isNullOrBlank()) {
                queryParts.add("'$folderId' in parents")
            }
            val query = queryParts.joinToString(" and ")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime,thumbnailLink,webViewLink)&pageSize=1"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(DriveApiException(response.code, "Search query error"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val first = files.getJSONObject(0)
                    val id = first.getString("id")
                    val name = first.optString("name", fileName)
                    val mime = first.optString("mimeType", "")
                    val size = first.optLong("size", 0L)
                    val webLink = first.optString("webViewLink", null)
                    return@withContext Result.success(
                        DriveFileResult(
                            fileId = id,
                            fileName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            webViewLink = webLink,
                            parentFolderId = folderId
                        )
                    )
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(
        accessToken: String,
        folderName: String,
        parentFolderId: String? = null
    ): Result<DriveFileResult> = withContext(Dispatchers.IO) {
        try {
            val metadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().apply { put(parentFolderId) })
                }
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,modifiedTime,webViewLink")
                .header("Authorization", "Bearer $accessToken")
                .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        DriveApiException(response.code, "Failed to create folder: ${response.code} $body")
                    )
                }
                val json = JSONObject(body)
                val id = json.getString("id")
                val name = json.optString("name", folderName)
                val mime = json.optString("mimeType", "application/vnd.google-apps.folder")
                val webLink = json.optString("webViewLink", null)
                Result.success(
                    DriveFileResult(
                        fileId = id,
                        fileName = name,
                        mimeType = mime,
                        sizeBytes = 0L,
                        isFolder = true,
                        webViewLink = webLink,
                        parentFolderId = parentFolderId
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(
        accessToken: String,
        folderId: String? = null,
        category: String? = null,
        pageSize: Int = 60
    ): Result<List<DriveFileResult>> = withContext(Dispatchers.IO) {
        try {
            val queryParts = mutableListOf("trashed = false")

            if (!folderId.isNullOrBlank()) {
                queryParts.add("'$folderId' in parents")
            }

            when (category?.uppercase()) {
                "IMAGES" -> queryParts.add("(mimeType contains 'image/' or mimeType = 'application/vnd.google-apps.folder')")
                "VIDEOS" -> queryParts.add("(mimeType contains 'video/' or mimeType = 'application/vnd.google-apps.folder')")
                "DOCUMENTS" -> queryParts.add("(mimeType contains 'pdf' or mimeType contains 'document' or mimeType contains 'sheet' or mimeType contains 'presentation' or mimeType contains 'text' or mimeType = 'application/vnd.google-apps.folder')")
                else -> { /* ALL */ }
            }

            val query = queryParts.joinToString(" and ")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime,thumbnailLink,webViewLink)&orderBy=folder,modifiedTime%20desc&pageSize=$pageSize"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        DriveApiException(response.code, "Failed to list files: ${response.code} $body")
                    )
                }

                val json = JSONObject(body)
                val filesArray = json.optJSONArray("files")
                val list = mutableListOf<DriveFileResult>()

                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val obj = filesArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val name = obj.optString("name", "Untitled")
                        val mime = obj.optString("mimeType", "")
                        val size = obj.optLong("size", 0L)
                        val thumb = obj.optString("thumbnailLink", null)
                        val webLink = obj.optString("webViewLink", null)
                        list.add(
                            DriveFileResult(
                                fileId = id,
                                fileName = name,
                                mimeType = mime,
                                sizeBytes = size,
                                isFolder = mime == "application/vnd.google-apps.folder",
                                thumbnailLink = thumb,
                                webViewLink = webLink,
                                parentFolderId = folderId
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFileResumable(
        accessToken: String,
        file: File,
        mimeType: String,
        fileName: String,
        folderId: String? = null,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<DriveFileResult> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext Result.failure(IOException("Local file does not exist or is unreadable: ${file.path}"))
        }

        try {
            // Check for existing file to prevent duplicates
            val existing = findExistingFile(accessToken, fileName, folderId).getOrNull()
            if (existing != null && existing.sizeBytes == file.length()) {
                return@withContext Result.success(existing)
            }

            val resolvedFolderId = resolveOrCreateFolderId(accessToken, folderId)

            // Step 1: Initiate resumable session with optional folder
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", mimeType)
                put("description", "Uploaded by Cloud Set Storage")
                if (!resolvedFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().apply { put(resolvedFolderId) })
                }
            }.toString()

            val initRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $accessToken")
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", file.length().toString())
                .post(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val uploadUrl: String = client.newCall(initRequest).execute().use { initResponse ->
                if (!initResponse.isSuccessful) {
                    val errBody = initResponse.body?.string().orEmpty()
                    return@withContext Result.failure(
                        DriveApiException(initResponse.code, "Failed to initiate upload: ${initResponse.code} $errBody")
                    )
                }
                val location = initResponse.header("Location")
                if (location.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Google Drive did not return upload session Location header"))
                }
                location
            }

            // Step 2: Upload file stream with progress counting & mandatory Content-Range header
            val fileLen = file.length()
            val contentRange = if (fileLen > 0) "bytes 0-${fileLen - 1}/$fileLen" else "bytes */0"
            val rawRequestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val countingBody = CountingRequestBody(rawRequestBody, onProgress)

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("Content-Range", contentRange)
                .put(countingBody)
                .build()

            client.newCall(uploadRequest).execute().use { uploadResponse ->
                val responseBody = uploadResponse.body?.string().orEmpty()
                if (uploadResponse.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val id = json.getString("id")
                    val name = json.optString("name", fileName)
                    val mime = json.optString("mimeType", mimeType)
                    val size = json.optLong("size", file.length())
                    val webLink = json.optString("webViewLink", null)
                    Result.success(
                        DriveFileResult(
                            fileId = id,
                            fileName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            isFolder = false,
                            webViewLink = webLink,
                            parentFolderId = resolvedFolderId
                        )
                    )
                } else {
                    Result.failure(
                        DriveApiException(uploadResponse.code, "Upload failed: ${uploadResponse.code} $responseBody")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findFolderByName(
        accessToken: String,
        folderName: String,
        parentFolderId: String? = null
    ): Result<DriveFileResult?> = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = folderName.replace("'", "\\'")
            val queryParts = mutableListOf(
                "name = '$sanitizedName'",
                "mimeType = 'application/vnd.google-apps.folder'",
                "trashed = false"
            )
            if (!parentFolderId.isNullOrBlank()) {
                queryParts.add("'$parentFolderId' in parents")
            }
            val query = queryParts.joinToString(" and ")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType)&pageSize=1"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.success(null)
                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val f = files.getJSONObject(0)
                    Result.success(
                        DriveFileResult(
                            fileId = f.getString("id"),
                            fileName = f.optString("name", folderName),
                            mimeType = "application/vnd.google-apps.folder",
                            sizeBytes = 0L,
                            isFolder = true
                        )
                    )
                } else {
                    Result.success(null)
                }
            }
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    suspend fun resolveOrCreateFolderId(
        accessToken: String,
        folderIdentifier: String?
    ): String? = withContext(Dispatchers.IO) {
        if (folderIdentifier.isNullOrBlank() || folderIdentifier.equals("Default", ignoreCase = true) || folderIdentifier.contains("Root", ignoreCase = true)) {
            return@withContext null
        }
        val existing = findFolderByName(accessToken, folderIdentifier).getOrNull()
        if (existing != null) {
            return@withContext existing.fileId
        }
        if (folderIdentifier.length >= 20 && !folderIdentifier.contains(" ")) {
            return@withContext folderIdentifier
        }
        val created = createFolder(accessToken, folderIdentifier).getOrNull()
        return@withContext created?.fileId
    }

    suspend fun trashFolder(
        accessToken: String,
        folderId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val metadata = JSONObject().apply {
                put("trashed", true)
            }
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$folderId")
                .header("Authorization", "Bearer $accessToken")
                .patch(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val body = response.body?.string().orEmpty()
                    Result.failure(DriveApiException(response.code, "Failed to trash folder: ${response.code} $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(
        accessToken: String,
        fileId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    Result.success(true)
                } else {
                    val body = response.body?.string().orEmpty()
                    Result.failure(DriveApiException(response.code, "Failed to delete file from Drive: ${response.code} $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class DriveApiException(val statusCode: Int, rawMessage: String) : Exception(rawMessage) {
    val isApiDisabled: Boolean
        get() = statusCode == 403 && (
            message?.contains("has not been used in project", ignoreCase = true) == true ||
            message?.contains("it is disabled", ignoreCase = true) == true ||
            message?.contains("SERVICE_DISABLED", ignoreCase = true) == true ||
            message?.contains("accessNotConfigured", ignoreCase = true) == true
        )

    val isQuotaExceeded: Boolean
        get() = statusCode == 403 && !isApiDisabled && (
            message?.contains("quota", ignoreCase = true) == true ||
            message?.contains("storage", ignoreCase = true) == true
        )

    val isAuthError: Boolean
        get() = statusCode == 401

    fun getActionableUserMessage(): String {
        return when {
            isApiDisabled -> "Google Drive API v3 is disabled in your Google Cloud Project. Please enable 'Google Drive API' in Google Cloud Console:\nhttps://console.cloud.google.com/apis/library/drive.googleapis.com"
            isQuotaExceeded -> "Your Google Drive storage is full. Please free up space in Google Drive."
            isAuthError -> "Google authentication expired. Please reconnect your account."
            else -> message ?: "Google Drive error (HTTP $statusCode)"
        }
    }
}
