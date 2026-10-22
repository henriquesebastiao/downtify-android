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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.BuildConfig
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.settings.ThemeMode
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.StatusPill
import com.henriquesebastiao.downtify.core.designsystem.theme.DmSans
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.GoogleSansFlex
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.AppFont
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
            onTheme = viewModel::setTheme,
            onDynamicColor = viewModel::setDynamicColor,
            onAmoled = viewModel::setAmoled,
            onFont = viewModel::setFont,
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
    val onTheme: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onAmoled: (Boolean) -> Unit = {},
    val onFont: (AppFont) -> Unit = {},
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
    Theme,
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painterResource(DowntifyIcons.Back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
            GroupedList(playbackRows(state) { dialog = it })
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
                        titleColor = MaterialTheme.colorScheme.error,
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
            onSelect = actions.onWifiQuality,
            onDismiss = { dialog = null },
        )

        SettingsDialog.MobileQuality -> ChoiceDialog(
            title = stringResource(R.string.settings_quality_mobile),
            options = state.qualityOptions,
            selected = state.settings.mobileQuality,
            label = { qualityLabel(it) },
            onSelect = actions.onMobileQuality,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = state.settings.theme,
            label = { stringResource(it.label) },
            onSelect = actions.onTheme,
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
            onSelect = actions.onOfflineLimit,
            onDismiss = { dialog = null },
        )

        SettingsDialog.ReadAhead -> ChoiceDialog(
            title = stringResource(R.string.settings_read_ahead),
            options = listOf(0, 1, 2, 3),
            selected = state.settings.readAhead.tracks,
            label = {
                if (it ==
                    0
                ) {
                    stringResource(R.string.settings_read_ahead_off)
                } else {
                    stringResource(R.string.settings_read_ahead_count, it)
                }
            },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCard(state: SettingsUiState, onChangeServer: () -> Unit, onOpenWeb: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DowntifyLogo(size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(state.serverName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Status, version and account: one line that wraps between items, never inside one.
            val details = listOfNotNull(
                state.version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.settings_server_version, it) },
                state.username.takeIf { it.isNotBlank() }?.let { stringResource(R.string.settings_signed_in_as, it) },
            ).joinToString(" · ")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionPill(state.connection)
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Stacked: the labels are long in some languages and would wrap inside a half-width button.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_change_server))
                }
                OutlinedButton(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_web))
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(connection: ConnectionState) {
    val scheme = MaterialTheme.colorScheme
    when (connection) {
        ConnectionState.Connected -> StatusPill(stringResource(R.string.settings_connected))

        ConnectionState.Unknown -> StatusPill(
            stringResource(R.string.settings_checking),
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
            dot = scheme.outline,
        )

        ConnectionState.Unreachable -> StatusPill(
            stringResource(R.string.settings_unreachable),
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
            dot = scheme.outline,
        )

        ConnectionState.DifferentServer -> StatusPill(
            stringResource(R.string.settings_different_server),
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            dot = scheme.error,
        )
    }
}

@Composable
private fun playbackRows(state: SettingsUiState, open: (SettingsDialog) -> Unit): List<GroupedRow> = listOf(
    GroupedRow(
        title = stringResource(R.string.settings_quality_wifi),
        summary = qualitySummary(state.settings.wifiQuality),
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
        summary = if (state.settings.readAhead.tracks == 0) {
            stringResource(R.string.settings_read_ahead_off)
        } else {
            stringResource(R.string.settings_read_ahead_count, state.settings.readAhead.tracks)
        },
        icon = DowntifyIcons.Downloads,
        action = RowAction.Click({ open(SettingsDialog.ReadAhead) }),
    ),
    GroupedRow(
        title = stringResource(R.string.settings_stream_cache),
        summary = stringResource(R.string.settings_stream_cache_size, state.settings.readAhead.cacheMb),
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

@Composable
private fun CacheLimitDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(current) { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_stream_cache)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_stream_cache_body))
                Text(stringResource(R.string.settings_stream_cache_size, value.roundToInt()))
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
            summary = stringResource(state.settings.theme.label),
            icon = DowntifyIcons.DarkMode,
            action = RowAction.Click({ open(SettingsDialog.Theme) }),
        ),
    )
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
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            label(option),
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

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    DowntifyTheme {
        val transcoding = Transcoding(true, listOf("aac", "mp3", "opus"), listOf(96, 128, 160, 192, 256, 320))
        SettingsScreen(
            state = SettingsUiState(
                serverName = "nas.local",
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
