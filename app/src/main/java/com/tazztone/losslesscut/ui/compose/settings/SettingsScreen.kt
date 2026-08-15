package com.tazztone.losslesscut.ui.compose.settings

import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.compose.theme.CyanAccent
import com.tazztone.losslesscut.ui.compose.theme.GreenAccent
import com.tazztone.losslesscut.ui.compose.theme.OrangeAccent
import com.tazztone.losslesscut.ui.compose.theme.PurpleAccent
import com.tazztone.losslesscut.ui.compose.theme.RedAccent
import com.tazztone.losslesscut.ui.compose.theme.YellowAccent
import com.tazztone.losslesscut.viewmodel.SettingsUiState
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onChangePath: () -> Unit,
    onResetPath: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onAccentColorChanged: (String) -> Unit,
    onUndoLimitChanged: (Int) -> Unit = {},
    onSnapshotFormatChanged: (Boolean) -> Unit = {},
    onJpgQualityChanged: (Int) -> Unit = {},
    onAutoExtractWaveformsChanged: (Boolean) -> Unit = {},
    onVisualFrameStepChanged: (Int) -> Unit = {},
    onCacheCapacityChanged: (Int) -> Unit = {},
    onCacheRetentionChanged: (Int) -> Unit = {},
    onClearCache: () -> Unit = {},
    onScrollChanged: (Int) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.value) {
        onScrollChanged(scrollState.value)
    }

    val isJpeg = uiState.snapshotFormat == "JPEG"

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

        // 🌐 1. Language & Region Category
        SettingsCategoryHeader(title = stringResource(R.string.category_language_region))

        LanguageSetting(
            currentLanguage = uiState.language,
            onLanguageChanged = onLanguageChanged
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // ⚡ 2. Performance & Smart Cut Category
        SettingsCategoryHeader(title = stringResource(R.string.category_performance))

        AutoExtractWaveformsSetting(
            autoExtract = uiState.autoExtractWaveforms,
            onToggled = onAutoExtractWaveformsChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        VisualFrameStepSetting(
            frameStep = uiState.visualFrameStep,
            onFrameStepChanged = onVisualFrameStepChanged
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // ✂️ 3. General & Editing Category
        SettingsCategoryHeader(title = stringResource(R.string.category_editing))

        LosslessModeSetting()

        Spacer(modifier = Modifier.height(16.dp))

        UndoLimitSetting(
            undoLimit = uiState.undoLimit,
            onUndoLimitChanged = onUndoLimitChanged
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 💾 4. Export & Snapshots Category
        SettingsCategoryHeader(title = stringResource(R.string.category_export))

        SnapshotFormatSetting(
            isJpeg = isJpeg,
            jpgQuality = uiState.jpgQuality,
            onFormatChanged = onSnapshotFormatChanged,
            onQualityChanged = onJpgQualityChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExportFolderSetting(
            customOutputUri = uiState.customOutputUri,
            onChangePath = onChangePath,
            onResetPath = onResetPath
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 🎨 5. Appearance Category
        SettingsCategoryHeader(title = stringResource(R.string.category_appearance))

        AccentColorSetting(
            currentAccentColor = uiState.accentColor,
            onAccentColorChanged = onAccentColorChanged
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // 💾 6. Analysis Cache Category
        SettingsCategoryHeader(title = stringResource(R.string.category_cache))

        CacheCapacitySetting(
            capacityMB = uiState.cacheCapacityMB,
            onCapacityChanged = onCacheCapacityChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        CacheRetentionSetting(
            retentionDays = uiState.cacheRetentionDays,
            onRetentionChanged = onCacheRetentionChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        CacheUsageAndClearSetting(
            usageBytes = uiState.cacheUsageBytes,
            isClearing = uiState.isClearingCache,
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
                        onClearCache()
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
fun LanguageSetting(
    currentLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.setting_language),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
        Text(
            text = stringResource(R.string.setting_language_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "system" to stringResource(R.string.language_system),
                "en" to stringResource(R.string.language_en),
                "de" to stringResource(R.string.language_de)
            ).forEach { (code, label) ->
                val isSelected = currentLanguage == code
                FilterChip(
                    selected = isSelected,
                    onClick = { onLanguageChanged(code) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun CacheCapacitySetting(
    capacityMB: Int,
    onCapacityChanged: (Int) -> Unit
) {
    var sliderValue by remember(capacityMB) { mutableStateOf(capacityMB.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_cache_capacity),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = "${sliderValue.toInt()} MiB",
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
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = { onCapacityChanged(sliderValue.toInt().coerceIn(50, 1000)) },
            valueRange = 50f..1000f,
            steps = 18
        )
    }
}

@Composable
fun CacheRetentionSetting(
    retentionDays: Int,
    onRetentionChanged: (Int) -> Unit
) {
    var sliderValue by remember(retentionDays) { mutableStateOf(retentionDays.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_cache_retention),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = "${sliderValue.toInt()} days",
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
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = { onRetentionChanged(sliderValue.toInt().coerceIn(1, 90)) },
            valueRange = 1f..90f,
            steps = 88
        )
    }
}

@Composable
fun CacheUsageAndClearSetting(
    usageBytes: Long,
    isClearing: Boolean,
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
        if (isClearing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            TextButton(onClick = onClearClicked) {
                Text(
                    text = stringResource(R.string.clear_analysis_cache),
                    color = MaterialTheme.colorScheme.error
                )
            }
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
    var sliderValue by remember(frameStep) { mutableStateOf(frameStep.toFloat()) }
    val currentStep = sliderValue.toInt().coerceIn(1, 30)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setting_visual_sample_interval),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = if (currentStep == 1) "1 frame" else "$currentStep frames",
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
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = { onFrameStepChanged(sliderValue.toInt().coerceIn(1, 30)) },
            valueRange = 1f..30f,
            steps = 28
        )
    }
}

@Composable
fun LosslessModeSetting() {
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
            checked = true,
            onCheckedChange = null,
            enabled = false
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
    var qualitySliderValue by remember(jpgQuality) { mutableStateOf(jpgQuality.toFloat()) }

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
                    text = "${qualitySliderValue.toInt()}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            }
            Slider(
                value = qualitySliderValue,
                onValueChange = { value -> qualitySliderValue = value },
                onValueChangeFinished = { onQualityChanged(qualitySliderValue.toInt().coerceIn(1, 100)) },
                valueRange = 1f..100f,
                steps = 98
            )
        }
    }
}

@Composable
fun UndoLimitSetting(
    undoLimit: Int,
    onUndoLimitChanged: (Int) -> Unit
) {
    var sliderValue by remember(undoLimit) { mutableStateOf(undoLimit.toFloat()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.undo_limit),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                text = "${sliderValue.toInt()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = { onUndoLimitChanged(sliderValue.toInt().coerceAtLeast(1)) },
            valueRange = 1f..100f,
            steps = 98
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
            "cyan" to (CyanAccent to "Cyan"),
            "purple" to (PurpleAccent to "Purple"),
            "green" to (GreenAccent to "Green"),
            "yellow" to (YellowAccent to "Yellow"),
            "red" to (RedAccent to "Red"),
            "orange" to (OrangeAccent to "Orange")
        ).forEach { (name, pair) ->
            val (color, colorName) = pair
            ColorCircle(
                colorName = colorName,
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
            text = if (customOutputUri != null) formatDisplayPath(customOutputUri) else stringResource(R.string.default_export_path),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

private fun formatDisplayPath(uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    val path = uri.path ?: uriString
    return when {
        path.contains("primary:") -> "Storage > " + Uri.decode(path.substringAfter("primary:"))
        path.contains("tree/") -> Uri.decode(path.substringAfter("tree/"))
        else -> Uri.decode(path)
    }
}

@Composable
fun ColorCircle(
    colorName: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(if (isSelected) 40.dp else 36.dp)
            .clip(CircleShape)
            .background(color)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = colorName
            }
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
