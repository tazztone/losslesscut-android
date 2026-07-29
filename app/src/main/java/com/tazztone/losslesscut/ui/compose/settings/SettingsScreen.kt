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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import java.util.Locale

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    analysisCache: IAnalysisCache? = null,
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
    val autoExtractWaveforms by preferences.autoExtractWaveformsFlow.collectAsStateWithLifecycle(initialValue = true)
    val visualFrameStep by preferences.defaultVisualFrameStepFlow.collectAsStateWithLifecycle(initialValue = 5)
    val cacheCapacityMB by preferences.cacheCapacityMBFlow.collectAsStateWithLifecycle(initialValue = 250)
    val cacheRetentionDays by preferences.cacheRetentionDaysFlow.collectAsStateWithLifecycle(initialValue = 30)

    var cacheUsageBytes by remember { mutableStateOf(0L) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(analysisCache) {
        cacheUsageBytes = kotlinx.coroutines.withContext(Dispatchers.IO) {
            analysisCache?.getCacheUsageBytes() ?: 0L
        }
    }

    val isJpeg = snapshotFormat == "JPEG"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        // Drag Handle
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

        Spacer(modifier = Modifier.height(20.dp))

        // ⚡ 1. Performance & Smart Cut Category
        SettingsCategoryHeader(title = stringResource(R.string.category_performance))

        AutoExtractWaveformsSetting(
            autoExtract = autoExtractWaveforms,
            onToggled = { enabled ->
                coroutineScope.launch {
                    preferences.setAutoExtractWaveforms(enabled)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        VisualFrameStepSetting(
            frameStep = visualFrameStep,
            onFrameStepChanged = { step ->
                coroutineScope.launch {
                    preferences.setDefaultVisualFrameStep(step)
                }
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // ✂️ 2. General & Editing Category
        SettingsCategoryHeader(title = stringResource(R.string.category_editing))

        LosslessModeSetting(
            isLossless = initialLosslessState,
            onToggled = onLosslessModeToggled
        )

        Spacer(modifier = Modifier.height(16.dp))

        UndoLimitSetting(
            undoLimit = undoLimit,
            onUndoLimitChanged = { value ->
                coroutineScope.launch {
                    preferences.setUndoLimit(value)
                }
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 💾 3. Export & Snapshots Category
        SettingsCategoryHeader(title = stringResource(R.string.category_export))

        SnapshotFormatSetting(
            isJpeg = isJpeg,
            jpgQuality = jpgQuality,
            onFormatChanged = { checked ->
                coroutineScope.launch {
                    preferences.setSnapshotFormat(if (checked) "JPEG" else "PNG")
                }
            },
            onQualityChanged = { value ->
                coroutineScope.launch {
                    preferences.setJpgQuality(value)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExportFolderSetting(
            customOutputUri = customOutputUri,
            onChangePath = onChangePath,
            onResetPath = onResetPath
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 🎨 4. Appearance Category
        SettingsCategoryHeader(title = stringResource(R.string.category_appearance))

        AccentColorSetting(
            currentAccentColor = currentAccentColor,
            onAccentColorChanged = onAccentColorChanged
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 💾 5. Analysis Cache Category
        SettingsCategoryHeader(title = stringResource(R.string.category_cache))

        CacheCapacitySetting(
            capacityMB = cacheCapacityMB,
            onCapacityChanged = { capacity ->
                coroutineScope.launch(Dispatchers.IO) {
                    preferences.setCacheCapacityMB(capacity)
                    cacheUsageBytes = analysisCache?.getCacheUsageBytes() ?: 0L
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CacheRetentionSetting(
            retentionDays = cacheRetentionDays,
            onRetentionChanged = { days ->
                coroutineScope.launch(Dispatchers.IO) {
                    preferences.setCacheRetentionDays(days)
                    cacheUsageBytes = analysisCache?.getCacheUsageBytes() ?: 0L
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CacheUsageAndClearSetting(
            usageBytes = cacheUsageBytes,
            onClearClicked = { showClearConfirmDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.clear_cache_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            analysisCache?.clearCache()
                            cacheUsageBytes = analysisCache?.getCacheUsageBytes() ?: 0L
                        }
                    }
                ) {
                    Text(stringResource(R.string.clear_analysis_cache), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun CacheCapacitySetting(
    capacityMB: Int,
    onCapacityChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_cache_capacity),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = "$capacityMB MiB",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }
        Text(
            text = stringResource(R.string.setting_cache_capacity_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = capacityMB.toFloat(),
            onValueChange = { value -> onCapacityChanged(value.toInt().coerceIn(50, 1000)) },
            valueRange = 50f..1000f
        )
    }
}

@Composable
fun CacheRetentionSetting(
    retentionDays: Int,
    onRetentionChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_cache_retention),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = "$retentionDays days",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }
        Text(
            text = stringResource(R.string.setting_cache_retention_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = retentionDays.toFloat(),
            onValueChange = { value -> onRetentionChanged(value.toInt().coerceIn(1, 90)) },
            valueRange = 1f..90f
        )
    }
}

@Composable
fun CacheUsageAndClearSetting(
    usageBytes: Long,
    onClearClicked: () -> Unit
) {
    val usageMiB = usageBytes / (1024f * 1024f)
    val usageText = String.format(Locale.ROOT, "%.2f MiB", usageMiB)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_cache_usage),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = usageText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        TextButton(onClick = onClearClicked) {
            Text(
                text = stringResource(R.string.clear_analysis_cache),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun AutoExtractWaveformsSetting(
    autoExtract: Boolean,
    onToggled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.setting_auto_extract_waveforms),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
        Switch(
            checked = autoExtract,
            onCheckedChange = onToggled
        )
    }

    Text(
        text = stringResource(R.string.setting_auto_extract_waveforms_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )
}

@Composable
fun VisualFrameStepSetting(
    frameStep: Int,
    onFrameStepChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_visual_sample_interval),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = if (frameStep == 1) "1 frame" else "$frameStep frames",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }
        Text(
            text = stringResource(R.string.setting_visual_sample_interval_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = frameStep.toFloat(),
            onValueChange = { value ->
                onFrameStepChanged(value.toInt().coerceIn(1, 30))
            },
            valueRange = 1f..30f
        )
    }
}

@Composable
fun LosslessModeSetting(
    isLossless: Boolean,
    onToggled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.lossless_mode_snap),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
        Switch(
            checked = isLossless,
            onCheckedChange = onToggled
        )
    }

    Text(
        text = stringResource(R.string.lossless_mode_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )
}

@Composable
fun SnapshotFormatSetting(
    isJpeg: Boolean,
    jpgQuality: Int,
    onFormatChanged: (Boolean) -> Unit,
    onQualityChanged: (Int) -> Unit
) {
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
            onCheckedChange = onFormatChanged
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
                onValueChange = { value -> onQualityChanged(value.toInt()) },
                valueRange = 1f..100f
            )
        }
    }
}

@Composable
fun UndoLimitSetting(
    undoLimit: Int,
    onUndoLimitChanged: (Int) -> Unit
) {
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
            onValueChange = { value -> onUndoLimitChanged(value.toInt().coerceAtLeast(1)) },
            valueRange = 1f..100f
        )
    }
}

@Composable
fun AccentColorSetting(
    currentAccentColor: String,
    onAccentColorChanged: (String) -> Unit
) {
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
}

@Composable
fun ExportFolderSetting(
    customOutputUri: String?,
    onChangePath: () -> Unit,
    onResetPath: () -> Unit
) {
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
