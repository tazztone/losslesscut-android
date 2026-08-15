package com.tazztone.losslesscut.ui.compose.export

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.compose.theme.CyanAccent
import com.tazztone.losslesscut.ui.compose.theme.GreenAccent
import com.tazztone.losslesscut.ui.compose.theme.OnSurfaceVariant
import com.tazztone.losslesscut.ui.compose.theme.SurfaceVariant

@Composable
fun ExportSuccessScreen(
    items: List<ExportedMediaItem>,
    isAudioOnly: Boolean,
    onShareSingle: (Uri) -> Unit,
    onShareAll: () -> Unit,
    onOpenMedia: (Uri) -> Unit,
    onDoneHome: () -> Unit,
    onContinueEditing: () -> Unit,
    onScrollChanged: (Int) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.value) {
        onScrollChanged(scrollState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(OnSurfaceVariant.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Success Header Badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(GreenAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_24),
                contentDescription = null,
                tint = GreenAccent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title & Summary
        Text(
            text = stringResource(R.string.export_complete_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.export_complete_message, items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Media Item(s) Display
        if (items.size == 1) {
            SingleMediaPreviewCard(
                item = items.first(),
                isAudioOnly = isAudioOnly,
                onOpenMedia = onOpenMedia
            )
        } else {
            MultipleMediaItemsList(
                items = items,
                isAudioOnly = isAudioOnly,
                onOpenMedia = onOpenMedia,
                onShareSingle = onShareSingle
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Share Action Button
        val shareLabel = if (items.size > 1) {
            stringResource(R.string.share_all_media, items.size)
        } else if (isAudioOnly) {
            stringResource(R.string.share_audio)
        } else {
            stringResource(R.string.share_video)
        }

        Button(
            onClick = {
                if (items.size > 1) onShareAll() else items.firstOrNull()?.let { onShareSingle(it.uri) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share_24),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = shareLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Actions
        OutlinedButton(
            onClick = onDoneHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.back_to_home),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onContinueEditing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.continue_editing),
                color = OnSurfaceVariant,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SingleMediaPreviewCard(
    item: ExportedMediaItem,
    isAudioOnly: Boolean,
    onOpenMedia: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant)
            .border(1.dp, OnSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon Box
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnailBitmap != null) {
                    Image(
                        bitmap = item.thumbnailBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            if (isAudioOnly) R.drawable.ic_audio_24 else R.drawable.ic_export_24
                        ),
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val details = listOfNotNull(item.formattedDuration, item.formattedSize).joinToString(" • ")
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onOpenMedia(item.uri) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    OnSurfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.open_media),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MultipleMediaItemsList(
    items: List<ExportedMediaItem>,
    isAudioOnly: Boolean,
    onOpenMedia: (Uri) -> Unit,
    onShareSingle: (Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant)
                    .border(1.dp, OnSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail / Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.thumbnailBitmap != null) {
                        Image(
                            bitmap = item.thumbnailBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isAudioOnly) R.drawable.ic_audio_24 else R.drawable.ic_export_24
                            ),
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val details = listOfNotNull(item.formattedDuration, item.formattedSize).joinToString(" • ")
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Quick Play Button
                IconButton(
                    onClick = { onOpenMedia(item.uri) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24),
                        contentDescription = stringResource(R.string.open_media),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quick Share Button
                IconButton(
                    onClick = { onShareSingle(item.uri) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share_24),
                        contentDescription = stringResource(R.string.share_media),
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

data class ExportedMediaItem(
    val uri: Uri,
    val fileName: String,
    val formattedSize: String? = null,
    val formattedDuration: String? = null,
    val thumbnailBitmap: Bitmap? = null
)

