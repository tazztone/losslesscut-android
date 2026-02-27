package com.tazztone.losslesscut.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.ui.compose.theme.CyanAccent
import com.tazztone.losslesscut.ui.compose.theme.GreenAccent
import com.tazztone.losslesscut.ui.compose.theme.OrangeAccent
import com.tazztone.losslesscut.ui.compose.theme.PurpleAccent
import com.tazztone.losslesscut.ui.compose.theme.RedAccent
import com.tazztone.losslesscut.ui.compose.theme.YellowAccent
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    initialLosslessState: Boolean,
    onLosslessModeToggled: (Boolean) -> Unit,
    onChangePath: () -> Unit,
    onResetPath: () -> Unit,
    onAccentColorChanged: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val undoLimit by preferences.undoLimitFlow.collectAsStateWithLifecycle(initialValue = 30)
    val snapshotFormat by preferences.snapshotFormatFlow.collectAsStateWithLifecycle(initialValue = "JPEG")
    val jpgQuality by preferences.jpgQualityFlow.collectAsStateWithLifecycle(initialValue = 95)
    val customOutputUri by preferences.customOutputUriFlow.collectAsStateWithLifecycle(initialValue = null)
    val currentAccentColor by preferences.accentColorFlow.collectAsStateWithLifecycle(initialValue = "cyan")

    val isJpeg = snapshotFormat == "JPEG"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .align(Alignment.CenterHorizontally)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.settings),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lossless Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lossless_mode_snap),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = initialLosslessState,
                onCheckedChange = onLosslessModeToggled
            )
        }

        Text(
            text = stringResource(R.string.lossless_mode_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Snapshot Format
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.save_snapshots_as_jpeg),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Switch(
                checked = isJpeg,
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        preferences.setSnapshotFormat(if (checked) "JPEG" else "PNG")
                    }
                }
            )
        }

        if (isJpeg) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.jpg_quality),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$jpgQuality",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
                Slider(
                    value = jpgQuality.toFloat(),
                    onValueChange = { value ->
                        coroutineScope.launch {
                            preferences.setJpgQuality(value.toInt())
                        }
                    },
                    valueRange = 1f..100f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Undo Limit
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.undo_limit),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
                Text(
                    text = "$undoLimit",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            }
            Slider(
                value = undoLimit.toFloat(),
                onValueChange = { value ->
                    coroutineScope.launch {
                        preferences.setUndoLimit(value.toInt().coerceAtLeast(1))
                    }
                },
                valueRange = 1f..100f
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )

        // Accent Color Picker
        Text(
            text = stringResource(R.string.theme_accent_color),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "cyan" to CyanAccent,
                "purple" to PurpleAccent,
                "green" to GreenAccent,
                "yellow" to YellowAccent,
                "red" to RedAccent,
                "orange" to OrangeAccent
            ).forEach { (name, color) ->
                ColorCircle(
                    color = color,
                    isSelected = currentAccentColor == name,
                    onClick = { onAccentColorChanged(name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export Folder
        Text(
            text = stringResource(R.string.export_folder),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = customOutputUri?.let { android.net.Uri.parse(it).path } ?: stringResource(R.string.default_export_path),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1
            )
            TextButton(onClick = onChangePath) {
                Text(stringResource(R.string.change))
            }
            if (customOutputUri != null) {
                IconButton(onClick = onResetPath) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore_24),
                        contentDescription = stringResource(R.string.reset),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(if (isSelected) 40.dp else 36.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
