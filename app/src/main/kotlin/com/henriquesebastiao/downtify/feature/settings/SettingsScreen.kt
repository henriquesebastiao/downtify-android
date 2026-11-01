package com.henriquesebastiao.downtify.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.BuildConfig
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.settings.ThemeMode
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.designsystem.component.ChoiceGroup
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SectionCard
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.theme.DmSans
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.GoogleSansFlex
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.designsystem.theme.withDataValues
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.model.NormalizationLevel
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.StreamFormat
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Transcoding
import com.henriquesebastiao.downtify.core.model.formatBytes
import com.henriquesebastiao.downtify.ui.common.GroupTitle
import com.henriquesebastiao.downtify.ui.common.GroupedList
import com.henriquesebastiao.downtify.ui.common.GroupedRow
import com.henriquesebastiao.downtify.ui.common.RowAction
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onWifiQuality = viewModel::setWifiQuality,
            onMobileQuality = viewModel::setMobileQuality,
            onNormalizeVolume = viewModel::setNormalizeVolume,
            onNormalizationLevel = viewModel::setNormalizationLevel,
            onTheme = viewModel::setTheme,
            onDynamicColor = viewModel::setDynamicColor,
            onAmoled = viewModel::setAmoled,
            onFont = viewModel::setFont,
            onFloatingNavBar = viewModel::setFloatingNavBar,
            onOpenAbout = onOpenAbout,
            onOpenLanguage = { openAppLanguageSettings(context) },
            onDownloadWifiOnly = viewModel::setDownloadWifiOnly,
            onOfflineLimit = viewModel::setOfflineLimit,
            onReadAhead = viewModel::setReadAhead,
            onStreamCacheMb = viewModel::setStreamCacheMb,
            onReadAheadMobile = viewModel::setReadAheadMobile,
            onUnpair = viewModel::unpair,
            onOpenWeb = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, state.baseUrl.toUri())) }
            },
        ),
        modifier = modifier,
    )
}

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onWifiQuality: (StreamQuality) -> Unit = {},
    val onMobileQuality: (StreamQuality) -> Unit = {},
    val onNormalizeVolume: (Boolean) -> Unit = {},
    val onNormalizationLevel: (NormalizationLevel) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onAmoled: (Boolean) -> Unit = {},
    val onFont: (AppFont) -> Unit = {},
    val onFloatingNavBar: (Boolean) -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onOpenLanguage: () -> Unit = {},
    val onDownloadWifiOnly: (Boolean) -> Unit = {},
    val onOfflineLimit: (Long) -> Unit = {},
    val onReadAhead: (Int) -> Unit = {},
    val onStreamCacheMb: (Int) -> Unit = {},
    val onReadAheadMobile: (Boolean) -> Unit = {},
    val onUnpair: () -> Unit = {},
    val onOpenWeb: () -> Unit = {},
)

private enum class SettingsDialog {
    WifiQuality,
    MobileQuality,
    Font,
    OfflineLimit,
    ReadAhead,
    StreamCache,
    Unpair,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PageHeader(
                title = stringResource(R.string.settings_title),
                onBack = actions.onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ServerCard(state, onChangeServer = { dialog = SettingsDialog.Unpair }, onOpenWeb = actions.onOpenWeb)

            GroupTitle(stringResource(R.string.settings_playback))
            GroupedList(playbackRows(state, actions) { dialog = it })
            Spacer(Modifier.height(Spacing.lg))
            GroupedList(bufferRows(state, actions) { dialog = it })

            GroupTitle(stringResource(R.string.settings_downloads))
            GroupedList(downloadRows(state, actions) { dialog = it })

            GroupTitle(stringResource(R.string.settings_appearance))
            GroupedList(appearanceRows(state, actions) { dialog = it })

            GroupTitle(stringResource(R.string.settings_about))
            GroupedList(
                listOf(
                    GroupedRow(
                        title = stringResource(R.string.settings_about_entry),
                        summary = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                        summaryValues = listOf(BuildConfig.VERSION_NAME),
                        icon = DowntifyIcons.Info,
                        action = RowAction.Click(actions.onOpenAbout, DowntifyIcons.ChevronRight),
                    ),
                ),
            )

            GroupTitle(stringResource(R.string.settings_server))
            GroupedList(
                listOf(
                    GroupedRow(
                        title = stringResource(R.string.settings_unpair),
                        summary = stringResource(R.string.settings_unpair_body),
                        icon = DowntifyIcons.Unpair,
                        destructive = true,
                        action = RowAction.Click({ dialog = SettingsDialog.Unpair }),
                    ),
                ),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    when (dialog) {
        SettingsDialog.WifiQuality -> ChoiceDialog(
            title = stringResource(R.string.settings_quality_wifi),
            options = state.qualityOptions,
            selected = state.settings.wifiQuality,
            label = { qualityLabel(it) },
            values = { qualityValues(it) },
            onSelect = actions.onWifiQuality,
            onDismiss = { dialog = null },
        )

        SettingsDialog.MobileQuality -> ChoiceDialog(
            title = stringResource(R.string.settings_quality_mobile),
            options = state.qualityOptions,
            selected = state.settings.mobileQuality,
            label = { qualityLabel(it) },
            values = { qualityValues(it) },
            onSelect = actions.onMobileQuality,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Font -> ChoiceDialog(
            title = stringResource(R.string.settings_font),
            options = AppFont.entries,
            selected = state.settings.font,
            label = { stringResource(it.label) },
            labelFont = { it.previewFamily },
            onSelect = actions.onFont,
            onDismiss = { dialog = null },
        )

        SettingsDialog.OfflineLimit -> ChoiceDialog(
            title = stringResource(R.string.settings_offline_limit),
            options = UserSettings.OFFLINE_LIMITS,
            selected = state.settings.offlineLimitBytes,
            label = { limitLabel(it) },
            values = { if (it == OfflinePlanner.UNLIMITED) emptyList() else listOf(limitLabel(it)) },
            onSelect = actions.onOfflineLimit,
            onDismiss = { dialog = null },
        )

        SettingsDialog.ReadAhead -> ChoiceDialog(
            title = stringResource(R.string.settings_read_ahead),
            options = listOf(0, 1, 2, 3),
            selected = state.settings.readAhead.tracks,
            label = { readAheadLabel(it) },
            onSelect = actions.onReadAhead,
            onDismiss = { dialog = null },
        )

        SettingsDialog.StreamCache -> CacheLimitDialog(
            state.settings.readAhead.cacheMb,
            actions.onStreamCacheMb,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Unpair -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(painterResource(DowntifyIcons.Unpair), contentDescription = null) },
            title = { Text(stringResource(R.string.settings_unpair_title)) },
            text = { Text(stringResource(R.string.settings_unpair_message)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    actions.onUnpair()
                }) { Text(stringResource(R.string.settings_unpair_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        null -> Unit
    }
}

/**
 * The paired server, the screen's one hero card: its name, its address in the data style, and its
 * state as tags (color, icon and words) that wrap between each other, never inside one.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerCard(state: SettingsUiState, onChangeServer: () -> Unit, onOpenWeb: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(
        hero = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DowntifyLogo(size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    state.serverName,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.address,
                    style = DowntifyType.dataMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ConnectionTag(state.connection)
            if (state.version.isNotBlank()) {
                Tag(stringResource(R.string.settings_server_version, state.version))
            }
        }
        if (state.username.isNotBlank()) {
            Text(
                stringResource(R.string.settings_signed_in_as, state.username),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        // Stacked: the labels are long in some languages and would wrap inside a half-width button.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = onChangeServer,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings_change_server))
            }
            OutlinedButton(
                onClick = onOpenWeb,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(
                    painterResource(DowntifyIcons.OpenInNew),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.settings_open_web))
            }
        }
    }
}

@Composable
private fun ConnectionTag(connection: ConnectionState) {
    when (connection) {
        ConnectionState.Connected ->
            Tag(stringResource(R.string.settings_connected), icon = DowntifyIcons.CheckCircle, tone = TagTone.Primary)

        ConnectionState.Unknown ->
            Tag(stringResource(R.string.settings_checking), icon = DowntifyIcons.Refresh)

        ConnectionState.Unreachable ->
            Tag(stringResource(R.string.settings_unreachable), icon = DowntifyIcons.Offline)

        ConnectionState.DifferentServer ->
            Tag(stringResource(R.string.settings_different_server), icon = DowntifyIcons.Warning, tone = TagTone.Error)
    }
}

@Composable
private fun playbackRows(
    state: SettingsUiState,
    actions: SettingsActions,
    open: (SettingsDialog) -> Unit,
): List<GroupedRow> = listOfNotNull(
    GroupedRow(
        title = stringResource(R.string.settings_normalize_volume),
        summary = stringResource(R.string.settings_normalize_volume_hint),
        icon = DowntifyIcons.Graphic,
        action = RowAction.Toggle(state.settings.normalizeVolume, actions.onNormalizeVolume),
    ),
    if (state.settings.normalizeVolume) {
        GroupedRow(
            title = stringResource(R.string.settings_normalization_level),
            summary = stringResource(R.string.settings_normalization_level_hint),
            action = RowAction.Control {
                ChoiceGroup(
                    options = NormalizationLevel.entries,
                    selected = state.settings.normalizationLevel,
                    label = { level ->
                        stringResource(
                            when (level) {
                                NormalizationLevel.Normal -> R.string.settings_normalization_normal
                                NormalizationLevel.Louder -> R.string.settings_normalization_louder
                                NormalizationLevel.Maximum -> R.string.settings_normalization_maximum
                            },
                        )
                    },
                    onSelect = actions.onNormalizationLevel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            },
        )
    } else {
        null
    },
    GroupedRow(
        title = stringResource(R.string.settings_quality_wifi),
        summary = qualitySummary(state.settings.wifiQuality),
        summaryValues = qualityValues(state.settings.wifiQuality),
        icon = DowntifyIcons.Wifi,
        action = RowAction.Click({ open(SettingsDialog.WifiQuality) }),
    ),
    GroupedRow(
        title = stringResource(R.string.settings_quality_mobile),
        summary = if (state.transcoding.available) {
            qualitySummary(state.settings.mobileQuality)
        } else {
            stringResource(R.string.settings_quality_no_transcoding)
        },
        summaryValues = if (state.transcoding.available) qualityValues(state.settings.mobileQuality) else emptyList(),
        icon = DowntifyIcons.Cellular,
        enabled = state.transcoding.available,
        action = RowAction.Click({ open(SettingsDialog.MobileQuality) }),
    ),
)

@Composable
private fun downloadRows(
    state: SettingsUiState,
    actions: SettingsActions,
    open: (SettingsDialog) -> Unit,
): List<GroupedRow> = listOf(
    GroupedRow(
        title = stringResource(R.string.settings_download_wifi_only),
        summary = stringResource(R.string.settings_download_wifi_only_body),
        icon = DowntifyIcons.Downloads,
        action = RowAction.Toggle(state.settings.downloadWifiOnly, actions.onDownloadWifiOnly),
    ),
    GroupedRow(
        title = stringResource(R.string.settings_offline_limit),
        summary = stringResource(
            R.string.settings_offline_limit_summary,
            limitLabel(state.settings.offlineLimitBytes),
            formatBytes(state.offlineUsedBytes),
        ),
        summaryValues = listOfNotNull(
            limitLabel(state.settings.offlineLimitBytes).takeIf {
                state.settings.offlineLimitBytes != OfflinePlanner.UNLIMITED
            },
            formatBytes(state.offlineUsedBytes),
        ),
        icon = DowntifyIcons.Storage,
        action = RowAction.Click({ open(SettingsDialog.OfflineLimit) }),
    ),
)

@Composable
private fun bufferRows(
    state: SettingsUiState,
    actions: SettingsActions,
    open: (SettingsDialog) -> Unit,
): List<GroupedRow> = listOf(
    GroupedRow(
        title = stringResource(R.string.settings_read_ahead),
        summary = readAheadLabel(state.settings.readAhead.tracks),
        icon = DowntifyIcons.Downloads,
        action = RowAction.Click({ open(SettingsDialog.ReadAhead) }),
    ),
    GroupedRow(
        title = stringResource(R.string.settings_stream_cache),
        summary = stringResource(R.string.settings_stream_cache_size, state.settings.readAhead.cacheMb),
        summaryValues = listOf(stringResource(R.string.settings_stream_cache_size, state.settings.readAhead.cacheMb)),
        icon = DowntifyIcons.Storage,
        action = RowAction.Click({ open(SettingsDialog.StreamCache) }),
    ),
    GroupedRow(
        title = stringResource(R.string.settings_read_ahead_mobile),
        summary = stringResource(R.string.settings_read_ahead_mobile_body),
        icon = DowntifyIcons.Cellular,
        enabled = state.settings.readAhead.tracks > 0,
        action = RowAction.Toggle(state.settings.readAhead.mobile, actions.onReadAheadMobile),
    ),
)

/** The cache size on a stepped slider (10 to 200 MB), the value over it; nothing is saved until Apply. */
@Composable
private fun CacheLimitDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(current) { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_stream_cache)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Text(stringResource(R.string.settings_stream_cache_body))
                Tag(
                    stringResource(R.string.settings_stream_cache_size, value.roundToInt()),
                    tone = TagTone.Secondary,
                )
                Slider(value = value, onValueChange = { value = it }, valueRange = 10f..200f, steps = 18)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(value.roundToInt())
                onDismiss()
            }) {
                Text(stringResource(R.string.settings_cache_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun appearanceRows(
    state: SettingsUiState,
    actions: SettingsActions,
    open: (SettingsDialog) -> Unit,
): List<GroupedRow> = buildList {
    add(
        GroupedRow(
            title = stringResource(R.string.settings_theme),
            summary = stringResource(R.string.settings_theme_body),
            icon = DowntifyIcons.DarkMode,
            action = RowAction.Control {
                ChoiceGroup(
                    options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
                    selected = state.settings.theme,
                    label = { stringResource(it.label) },
                    onSelect = actions.onTheme,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            },
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(
            GroupedRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_body),
                icon = DowntifyIcons.Palette,
                action = RowAction.Toggle(state.settings.dynamicColor, actions.onDynamicColor),
            ),
        )
    }
    add(
        GroupedRow(
            title = stringResource(R.string.settings_amoled),
            summary = stringResource(R.string.settings_amoled_body),
            icon = DowntifyIcons.Contrast,
            action = RowAction.Toggle(state.settings.amoled, actions.onAmoled),
        ),
    )
    add(
        GroupedRow(
            title = stringResource(R.string.settings_font),
            summary = stringResource(state.settings.font.label),
            icon = DowntifyIcons.Font,
            action = RowAction.Click({ open(SettingsDialog.Font) }),
        ),
    )
    add(
        GroupedRow(
            title = stringResource(R.string.settings_floating_nav_bar),
            summary = stringResource(R.string.settings_floating_nav_bar_body),
            icon = DowntifyIcons.FloatingBar,
            action = RowAction.Toggle(state.settings.floatingNavBar, actions.onFloatingNavBar),
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            GroupedRow(
                title = stringResource(R.string.settings_language),
                summary = stringResource(R.string.settings_language_summary, currentLanguageName()),
                icon = DowntifyIcons.Language,
                action = RowAction.Click(actions.onOpenLanguage, DowntifyIcons.OpenInNew),
            ),
        )
    }
}

/** The name of the language the app shows now, in that language. */
@Composable
private fun currentLanguageName(): String {
    val locale = LocalLocale.current.platformLocale
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Android 13+ keeps a language per app; its screen is where the user picks it. */
private fun openAppLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    values: @Composable (T) -> List<String> = { emptyList() },
    labelFont: (T) -> FontFamily? = { null },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) {
                                onSelect(option)
                                onDismiss()
                            }
                            .heightIn(min = 48.dp)
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            withDataValues(label(option), values(option)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = labelFont(option),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun qualitySummary(quality: StreamQuality): String = if (quality.isOriginal) {
    stringResource(R.string.settings_quality_original)
} else {
    stringResource(R.string.settings_quality_transcoded, quality.format.displayName, quality.bitrate)
}

@Composable
private fun qualityLabel(quality: StreamQuality): String = if (quality.isOriginal) {
    stringResource(R.string.settings_quality_original)
} else {
    stringResource(R.string.settings_quality_label, quality.format.displayName, quality.bitrate)
}

/** A transcoded quality's format and bitrate, in tabular figures; the " · " between them isn't. */
@Composable
private fun qualityValues(quality: StreamQuality): List<String> =
    if (quality.isOriginal) emptyList() else qualityLabel(quality).split(" · ")

@Composable
private fun readAheadLabel(tracks: Int): String = if (tracks == 0) {
    stringResource(R.string.settings_read_ahead_off)
} else {
    stringResource(R.string.settings_read_ahead_count, tracks)
}

private val StreamFormat.displayName: String
    get() = when (this) {
        StreamFormat.Original -> "Original"
        StreamFormat.Opus -> "Opus"
        StreamFormat.Aac -> "AAC"
        StreamFormat.Mp3 -> "MP3"
    }

private val AppFont.label: Int
    get() = when (this) {
        AppFont.Downtify -> R.string.settings_font_downtify
        AppFont.System -> R.string.settings_font_system
        AppFont.GoogleSansFlex -> R.string.settings_font_google_sans_flex
    }

/** Each option in its own typeface, so the list is its own preview. */
private val AppFont.previewFamily: FontFamily
    get() = when (this) {
        AppFont.Downtify -> DmSans
        AppFont.System -> FontFamily.Default
        AppFont.GoogleSansFlex -> GoogleSansFlex
    }

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Dark -> R.string.settings_theme_dark
        ThemeMode.Light -> R.string.settings_theme_light
    }

@PhonePreviews
@Composable
private fun SettingsPreview() {
    DowntifyTheme {
        val transcoding = Transcoding(true, listOf("aac", "mp3", "opus"), listOf(96, 128, 160, 192, 256, 320))
        SettingsScreen(
            state = SettingsUiState(
                serverName = "nas.local",
                username = "admin",
                address = "192.168.1.20:8000",
                version = "3.2.0",
                apiVersion = 1,
                connection = ConnectionState.Connected,
                transcoding = transcoding,
                qualityOptions = SettingsViewModel.qualityOptions(transcoding),
            ),
            actions = SettingsActions(),
        )
    }
}

@Composable
private fun limitLabel(bytes: Long): String = if (bytes == OfflinePlanner.UNLIMITED) {
    stringResource(R.string.settings_offline_limit_none)
} else {
    formatBytes(bytes)
}
