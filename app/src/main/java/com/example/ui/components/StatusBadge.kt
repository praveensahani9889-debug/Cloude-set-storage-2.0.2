package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.UploadState
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun StatusBadge(
    state: UploadState,
    modifier: Modifier = Modifier,
    progress: Int = 0
) {
    val (bgColor, textColor, label) = when (state) {
        UploadState.UPLOADED -> Triple(StatusSuccess.copy(alpha = 0.2f), StatusSuccess, "Uploaded")
        UploadState.UPLOADING -> Triple(StatusInfo.copy(alpha = 0.2f), StatusInfo, if (progress > 0) "Uploading $progress%" else "Uploading")
        UploadState.QUEUED -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, "Queued")
        UploadState.RETRY_WAIT -> Triple(StatusWarning.copy(alpha = 0.2f), StatusWarning, "Retry Wait")
        UploadState.FAILED -> Triple(StatusError.copy(alpha = 0.2f), StatusError, "Failed")
        UploadState.NEEDS_AUTH -> Triple(StatusError.copy(alpha = 0.2f), StatusError, "Needs Auth")
        UploadState.CANCELED -> Triple(Color.Gray.copy(alpha = 0.2f), Color.Gray, "Canceled")
        UploadState.LOCAL_SAVED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Local")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
