package com.tazztone.losslesscut.ui.compose.settings

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.tazztone.losslesscut.BuildConfig
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.compose.theme.CyanAccent
import com.tazztone.losslesscut.ui.compose.theme.GreenAccent
import com.tazztone.losslesscut.ui.compose.theme.OrangeAccent
import com.tazztone.losslesscut.ui.compose.theme.PurpleAccent
import com.tazztone.losslesscut.ui.compose.theme.RedAccent
import com.tazztone.losslesscut.ui.compose.theme.YellowAccent
import com.tazztone.losslesscut.viewmodel.SettingsUiState
import java.util.Locale

private data class AccentColorOption(
    val id: String,
    val name: String,
    val color: Color
)

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
    onDeleteOriginalAfterExportChanged: (Boolean) -> Unit = {},
    onAutoExtractWaveformsChanged: (Boolean) -> Unit = {},
    onVisualFrameStepChanged: (Int) -> Unit = {},
    onCacheCapacityChanged: (Int) -> Unit = {},
    onCacheRetentionChanged: (Int) -> Unit = {},
    onClearCache: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onScrollChanged: (Int) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.value) {
        onScrollChanged(scrollState.value)
    }

    val isJpeg = uiState.snapshotFormat == "JPEG"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag Handle for BottomSheet
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )

            // Header Bar with Title and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_remove_24),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ✂️ 1. Workflow & Editing Card
            SettingsSectionCard(
                icon = R.drawable.ic_split_24,
                title = stringResource(R.string.category_editing)
            ) {
                LosslessModeStatusCard()

                Spacer(modifier = Modifier.height(16.dp))

                SettingSliderWithPresets(
                    title = stringResource(R.string.undo_limit),
                    description = "Maximum undo/redo history steps preserved during editing.",
                    currentValue = uiState.undoLimit,
                    displayValue = "${uiState.undoLimit}",
                    valueRange = 1f..100f,
                    presets = listOf(10 to "10", 25 to "25", 50 to "50", 100 to "100"),
                    onValueChanged = onUndoLimitChanged
                )
            }

            // 💾 2. Export & Storage Card
            SettingsSectionCard(
                icon = R.drawable.ic_export_24,
                title = stringResource(R.string.category_export)
            ) {
                ExportFolderSetting(
                    customOutputUri = uiState.customOutputUri,
                    onChangePath = onChangePath,
                    onResetPath = onResetPath
                )

                SettingsDivider()

                SnapshotFormatSetting(
                    isJpeg = isJpeg,
                    jpgQuality = uiState.jpgQuality,
                    onFormatChanged = onSnapshotFormatChanged,
                    onQualityChanged = onJpgQualityChanged
                )

                SettingsDivider()

                DeleteOriginalDefaultSetting(
                    deleteOriginal = uiState.deleteOriginalAfterExport,
                    onToggled = onDeleteOriginalAfterExportChanged
                )
            }

            // ⚡ 3. Performance & Analysis Cache Card
            SettingsSectionCard(
                icon = R.drawable.ic_smart_cut_24,
                title = stringResource(R.string.category_performance)
            ) {
                AutoExtractWaveformsSetting(
                    autoExtract = uiState.autoExtractWaveforms,
                    onToggled = onAutoExtractWaveformsChanged
                )

                SettingsDivider()

                SettingSliderWithPresets(
                    title = stringResource(R.string.setting_visual_sample_interval),
                    description = stringResource(R.string.setting_visual_sample_interval_desc),
                    currentValue = uiState.visualFrameStep,
                    displayValue = if (uiState.visualFrameStep == 1) "1 frame" else "${uiState.visualFrameStep} frames",
                    valueRange = 1f..30f,
                    presets = listOf(1 to "1f", 5 to "5f", 10 to "10f", 15 to "15f", 30 to "30f"),
                    onValueChanged = onVisualFrameStepChanged
                )

                SettingsDivider()

                SettingSliderWithPresets(
                    title = stringResource(R.string.setting_cache_capacity),
                    description = stringResource(R.string.setting_cache_capacity_desc),
                    currentValue = uiState.cacheCapacityMB,
                    displayValue = "${uiState.cacheCapacityMB} MiB",
                    valueRange = 50f..1000f,
                    presets = listOf(100 to "100 MB", 250 to "250 MB", 500 to "500 MB", 1000 to "1 GB"),
                    onValueChanged = onCacheCapacityChanged
                )

                SettingsDivider()

                SettingSliderWithPresets(
                    title = stringResource(R.string.setting_cache_retention),
                    description = stringResource(R.string.setting_cache_retention_desc),
                    currentValue = uiState.cacheRetentionDays,
                    displayValue = "${uiState.cacheRetentionDays} days",
                    valueRange = 1f..90f,
                    presets = listOf(7 to "7d", 14 to "14d", 30 to "30d", 90 to "90d"),
                    onValueChanged = onCacheRetentionChanged
                )

                SettingsDivider()

                CacheUsageAndClearSetting(
                    usageBytes = uiState.cacheUsageBytes,
                    isClearing = uiState.isClearingCache,
                    onClearClicked = { showClearConfirmDialog = true }
                )
            }

            // 🎨 4. Appearance & Language Card
            SettingsSectionCard(
                icon = R.drawable.ic_save_color_24,
                title = stringResource(R.string.category_appearance)
            ) {
                AccentColorSetting(
                    currentAccentColor = uiState.accentColor,
                    onAccentColorChanged = onAccentColorChanged
                )

                SettingsDivider()

                LanguageSetting(
                    currentLanguage = uiState.language,
                    onLanguageChanged = onLanguageChanged
                )
            }

            // ℹ️ 5. About & System Card
            SettingsSectionCard(
                icon = R.drawable.ic_info_24,
                title = stringResource(R.string.category_about)
            ) {
                AboutSystemContent(
                    onOpenUrl = onOpenUrl
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
fun SettingsSectionCard(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, Color(0xFF222838))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        thickness = 1.dp,
        color = Color(0xFF222838)
    )
}

@Composable
fun LosslessModeStatusCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.lossless_mode_status_title),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.lossless_mode_status_desc),
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingSliderWithPresets(
    title: String,
    description: String,
    currentValue: Int,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    presets: List<Pair<Int, String>>,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentValue) { mutableStateOf(currentValue.toFloat()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChanged(sliderValue.toInt()) },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { (presetVal, label) ->
                val isSelected = currentValue == presetVal
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1B2232),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF26324A)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            sliderValue = presetVal.toFloat()
                            onValueChanged(presetVal)
                        }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExportFolderSetting(
    customOutputUri: String?,
    onChangePath: () -> Unit,
    onResetPath: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.export_folder),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (customOutputUri != null) formatDisplayPath(customOutputUri) else stringResource(R.string.default_export_path),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SnapshotFormatSetting(
    isJpeg: Boolean,
    jpgQuality: Int,
    onFormatChanged: (Boolean) -> Unit,
    onQualityChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.save_snapshots_as_jpeg),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isJpeg,
                onClick = { onFormatChanged(false) },
                label = { Text(stringResource(R.string.format_png_lossless)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            FilterChip(
                selected = isJpeg,
                onClick = { onFormatChanged(true) },
                label = { Text(stringResource(R.string.format_jpeg_compressed)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        AnimatedVisibility(
            visible = isJpeg,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                SettingSliderWithPresets(
                    title = stringResource(R.string.jpg_quality),
                    description = "Compression quality for saved JPEG snapshots.",
                    currentValue = jpgQuality,
                    displayValue = "$jpgQuality%",
                    valueRange = 1f..100f,
                    presets = listOf(75 to "75%", 85 to "85%", 95 to "95%", 100 to "100%"),
                    onValueChanged = onQualityChanged
                )
            }
        }
    }
}

@Composable
fun DeleteOriginalDefaultSetting(
    deleteOriginal: Boolean,
    onToggled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_delete_original_default),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.setting_delete_original_default_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = deleteOriginal,
            onCheckedChange = onToggled
        )
    }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_auto_extract_waveforms),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.setting_auto_extract_waveforms_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = autoExtract,
            onCheckedChange = onToggled
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = usageText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedButton(
            onClick = onClearClicked,
            enabled = !isClearing
        ) {
            if (isClearing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.clearing_cache),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.clear_analysis_cache),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AccentColorSetting(
    currentAccentColor: String,
    onAccentColorChanged: (String) -> Unit
) {
    val colorOptions = remember {
        listOf(
            AccentColorOption("cyan", "Cyan", CyanAccent),
            AccentColorOption("purple", "Purple", PurpleAccent),
            AccentColorOption("green", "Green", GreenAccent),
            AccentColorOption("yellow", "Yellow", YellowAccent),
            AccentColorOption("red", "Red", RedAccent),
            AccentColorOption("orange", "Orange", OrangeAccent)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_accent_color),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colorOptions.forEach { option ->
                ColorCircle(
                    colorName = option.name,
                    color = option.color,
                    isSelected = currentAccentColor == option.id,
                    onClick = { onAccentColorChanged(option.id) }
                )
            }
        }
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.setting_language_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun AboutSystemContent(
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E2538),
                border = BorderStroke(1.dp, Color(0xFF2E3850))
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.about_engine_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.about_engine_desc),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        SettingsDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onOpenUrl("https://github.com/mifi/lossless-cut") },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.about_github),
                    style = MaterialTheme.typography.labelSmall
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
            .size(48.dp)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = colorName
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

