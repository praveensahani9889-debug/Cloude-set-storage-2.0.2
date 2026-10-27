package com.example.data.local.model

enum class UploadState {
    LOCAL_SAVED,
    QUEUED,
    UPLOADING,
    UPLOADED,
    RETRY_WAIT,
    FAILED,
    CANCELED,
    NEEDS_AUTH
}
