package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.BuildConfig
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.settings.ThemeMode
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.model.NormalizationLevel
import com.henriquesebastiao.downtify.feature.settings.SettingsActions
import com.henriquesebastiao.downtify.feature.settings.SettingsUiState
import com.henriquesebastiao.downtify.feature.settings.SettingsViewModel
import com.henriquesebastiao.downtify.feature.settings.currentLanguageName
import com.henriquesebastiao.downtify.feature.settings.label
import com.henriquesebastiao.downtify.feature.settings.openAppLanguageSettings
import com.henriquesebastiao.downtify.feature.settings.previewFamily
import com.henriquesebastiao.downtify.feature.settings.qualityLabel
import com.henriquesebastiao.downtify.feature.settings.qualitySummary
import com.henriquesebastiao.downtify.feature.settings.readAheadLabel
import com.henriquesebastiao.downtify.ui.common.chooseClientCertificate

/** Settings on a TV: the phone's [SettingsViewModel], drawn for a TV by [TvSettingsScreen]. */
@Composable
fun TvSettingsRoute(onOpenAbout: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TvSettingsScreen(
        state = state,
        actions = SettingsActions(
            onWifiQuality = viewModel::setWifiQuality,
            onNormalizeVolume = viewModel::setNormalizeVolume,
            onNormalizationLevel = viewModel::setNormalizationLevel,
            onTheme = viewModel::setTheme,
            onDynamicColor = viewModel::setDynamicColor,
            onAmoled = viewModel::setAmoled,
            onFont = viewModel::setFont,
            onOpenAbout = onOpenAbout,
            onOpenLanguage = { openAppLanguageSettings(context) },
            onReadAhead = viewModel::setReadAhead,
            onStreamCacheMb = viewModel::setStreamCacheMb,
            onSelectClientCertificate = {
                chooseClientCertificate(
                    context = context,
                    currentAlias = state.clientCertificate,
                    addressOrUrl = state.baseUrl,
                    onSelected = viewModel::setClientCertificate,
                )
            },
            onClearClientCertificate = { viewModel.setClientCertificate(null) },
            onUnpair = viewModel::unpair,
        ),
        modifier = modifier,
    )
}

/** The settings' sections, down the left of the TV screen. */
internal enum class TvSettingsSection(@StringRes val title: Int, val icon: Int) {
    Server(R.string.settings_server, DowntifyIcons.Server),
    Playback(R.string.settings_playback, DowntifyIcons.Graphic),
    Appearance(R.string.settings_appearance, DowntifyIcons.Palette),
    About(R.string.settings_about, DowntifyIcons.Info),
}

private enum class TvSettingsDialog { WifiQuality, NormalizationLevel, ReadAhead, StreamCache, Theme, Font, ClientCertificate, Unpair }

/**
 * Settings on a TV, as TV settings screens lay them out: the sections down the left (focus on one
 * shows its options), its options on the right, each a row that rings when focused; a choice opens
 * a short list over the screen. What a TV has no use for stays on the phone: the floating
 * navigation bar, mobile data, offline downloads and the server's web page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvSettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf(TvSettingsSection.Server) }
    var dialog by rememberSaveable { mutableStateOf<TvSettingsDialog?>(null) }
    val tabFocus = remember { TvSettingsSection.entries.associateWith { FocusRequester() } }
    // The first option of the section showing: where focus enters from the sections.
    val firstOption = remember { FocusRequester() }
    val first = Modifier.focusRequester(firstOption)
    LaunchedEffect(Unit) { tabFocus.getValue(section).requestFocusWhenPlaced() }
    val focusManager = LocalFocusManager.current

    Row(modifier.fillMaxSize().padding(start = TvMargin - TvRingSpace, top = Spacing.md)) {
        Column(Modifier.width(SectionsWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = TvRingSpace, bottom = Spacing.md).semantics { heading() },
            )
            TvSettingsSection.entries.forEach { entry ->
                SectionTab(
                    section = entry,
                    selected = entry == section,
                    onFocused = { section = entry },
                    // A click (or a touch) picks the section too, not only the focus on it.
                    onClick = {
                        section = entry
                        focusManager.moveFocus(FocusDirection.Right)
                    },
                    modifier = Modifier.focusRequester(tabFocus.getValue(entry)),
                )
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvKeepVisibleSpec(KeepVisible)) {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.xxl, end = TvMargin - TvRingSpace, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // In from the sections lands on the first option, not the one level with the section;
                    // left goes back to their section; down past the last option stays put (it used to
                    // fall into the sections and switch them).
                    .focusProperties {
                        onEnter = { firstOption.requestFocus() }
                        onExit = {
                            when (requestedFocusDirection) {
                                FocusDirection.Left -> tabFocus.getValue(section).requestFocus()
                                FocusDirection.Down -> cancelFocusChange()
                                else -> Unit
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                when (section) {
                    TvSettingsSection.Server -> serverRows(state, first) { dialog = it }
                    TvSettingsSection.Playback -> playbackRows(state, actions, first) { dialog = it }
                    TvSettingsSection.Appearance -> appearanceRows(state, actions, first) { dialog = it }
                    TvSettingsSection.About -> aboutRows(actions, first)
                }
            }
        }
    }
    SettingsDialogs(dialog, state, actions) { dialog = null }
}

private fun LazyListScope.serverRows(state: SettingsUiState, first: Modifier, open: (TvSettingsDialog) -> Unit) {
    item(key = "server") { ServerHeader(state) }
    item(key = "certificate") {
        SettingRow(
            title = stringResource(R.string.settings_client_certificate),
            summary = state.clientCertificate ?: stringResource(R.string.settings_client_certificate_none),
            icon = DowntifyIcons.Terms,
            onClick = { open(TvSettingsDialog.ClientCertificate) },
            modifier = first,
        )
    }
    item(key = "unpair") {
        SettingRow(
            title = stringResource(R.string.settings_unpair),
            summary = stringResource(R.string.settings_change_server),
            icon = DowntifyIcons.Unpair,
            destructive = true,
            onClick = { open(TvSettingsDialog.Unpair) },
        )
    }
}

private fun LazyListScope.playbackRows(
    state: SettingsUiState,
    actions: SettingsActions,
    first: Modifier,
    open: (TvSettingsDialog) -> Unit,
) {
    val settings = state.settings
    item(key = "normalize") {
        SettingRow(
            title = stringResource(R.string.settings_normalize_volume),
            summary = stringResource(R.string.settings_normalize_volume_hint),
            icon = DowntifyIcons.Graphic,
            toggle = settings.normalizeVolume,
            onClick = { actions.onNormalizeVolume(!settings.normalizeVolume) },
            modifier = first,
        )
    }
    if (settings.normalizeVolume) {
        item(key = "level") {
            SettingRow(
                title = stringResource(R.string.settings_normalization_level),
                summary = stringResource(settings.normalizationLevel.label),
                icon = DowntifyIcons.Graphic,
                chevron = true,
                onClick = { open(TvSettingsDialog.NormalizationLevel) },
            )
        }
    }
    item(key = "quality") {
        SettingRow(
            title = stringResource(R.string.settings_quality_wifi),
            summary = qualitySummary(settings.wifiQuality),
            icon = DowntifyIcons.Wifi,
            chevron = true,
            onClick = { open(TvSettingsDialog.WifiQuality) },
        )
    }
    item(key = "readAhead") {
        SettingRow(
            title = stringResource(R.string.settings_read_ahead),
            summary = readAheadLabel(settings.readAhead.tracks),
            icon = DowntifyIcons.Downloads,
            chevron = true,
            onClick = { open(TvSettingsDialog.ReadAhead) },
        )
    }
    item(key = "cache") {
        SettingRow(
            title = stringResource(R.string.settings_stream_cache),
            summary = stringResource(R.string.settings_stream_cache_size, settings.readAhead.cacheMb),
            icon = DowntifyIcons.Storage,
            chevron = true,
            onClick = { open(TvSettingsDialog.StreamCache) },
        )
    }
}

private fun LazyListScope.appearanceRows(
    state: SettingsUiState,
    actions: SettingsActions,
    first: Modifier,
    open: (TvSettingsDialog) -> Unit,
) {
    val settings = state.settings
    item(key = "theme") {
        SettingRow(
            title = stringResource(R.string.settings_theme),
            summary = stringResource(settings.theme.label),
            icon = DowntifyIcons.DarkMode,
            chevron = true,
            onClick = { open(TvSettingsDialog.Theme) },
            modifier = first,
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item(key = "dynamic") {
            SettingRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_body),
                icon = DowntifyIcons.Palette,
                toggle = settings.dynamicColor,
                onClick = { actions.onDynamicColor(!settings.dynamicColor) },
            )
        }
    }
    item(key = "amoled") {
        SettingRow(
            title = stringResource(R.string.settings_amoled),
            summary = stringResource(R.string.settings_amoled_body),
            icon = DowntifyIcons.Contrast,
            toggle = settings.amoled,
            onClick = { actions.onAmoled(!settings.amoled) },
        )
    }
    item(key = "font") {
        SettingRow(
            title = stringResource(R.string.settings_font),
            summary = stringResource(settings.font.label),
            icon = DowntifyIcons.Font,
            chevron = true,
            onClick = { open(TvSettingsDialog.Font) },
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item(key = "language") {
            SettingRow(
                title = stringResource(R.string.settings_language),
                summary = stringResource(R.string.settings_language_summary, currentLanguageName()),
                icon = DowntifyIcons.Language,
                chevron = true,
                onClick = actions.onOpenLanguage,
            )
        }
    }
}

private fun LazyListScope.aboutRows(actions: SettingsActions, first: Modifier) {
    item(key = "about") {
        SettingRow(
            title = stringResource(R.string.settings_about_entry),
            summary = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
            icon = DowntifyIcons.Info,
            chevron = true,
            onClick = actions.onOpenAbout,
            modifier = first,
        )
    }
}

/** The paired server: its name, address, state and account. Not focusable: it's what the rows act on. */
@Composable
private fun ServerHeader(state: SettingsUiState) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(TvRingSpace)
            .background(scheme.surfaceContainer, RowShape)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            DowntifyLogo(size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    state.serverName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ConnectionTag(state.connection)
            if (state.version.isNotBlank()) Tag(stringResource(R.string.settings_server_version, state.version))
        }
        if (state.username.isNotBlank()) {
            Text(
                stringResource(R.string.settings_signed_in_as, state.username),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionTag(connection: ConnectionState) {
    when (connection) {
        ConnectionState.Connected ->
            Tag(stringResource(R.string.settings_connected), icon = DowntifyIcons.CheckCircle, tone = TagTone.Primary)

        ConnectionState.Unknown -> Tag(stringResource(R.string.settings_checking), icon = DowntifyIcons.Refresh)

        ConnectionState.Unreachable -> Tag(stringResource(R.string.settings_unreachable), icon = DowntifyIcons.Offline)

        ConnectionState.DifferentServer ->
            Tag(stringResource(R.string.settings_different_server), icon = DowntifyIcons.Warning, tone = TagTone.Error)
    }
}

/** A section on the left: focusing it shows its options; OK moves into them. */
@Composable
private fun SectionTab(
    section: TvSettingsSection,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val content = if (selected || focused) scheme.onSurface else scheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusFrame(focused, RowRingShape)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .background(if (selected) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f), RowShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(painterResource(section.icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(
            stringResource(section.title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * An option: its icon, its title over what it's set to (or what it does), and a switch ([toggle]
 * not null) or a chevron at the end. The whole row is one focusable, ringed when focused.
 */
@Suppress("LongParameterList")
@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    toggle: Boolean? = null,
    chevron: Boolean = false,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusFrame(focused, RowRingShape)
            .clickable(
                interaction,
                indication = null,
                onClickLabel = title,
                role = if (toggle != null) Role.Switch else Role.Button,
                onClick = onClick,
            )
            .background(if (focused) scheme.surfaceContainerHighest else scheme.surfaceContainer, RowShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(
            Modifier
                .size(IconCircle)
                .background(if (destructive) scheme.errorContainer else scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (destructive) scheme.onErrorContainer else scheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (destructive) scheme.error else scheme.onSurface,
            )
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2)
            }
        }
        when {
            toggle != null -> Switch(checked = toggle, onCheckedChange = null)
            chevron -> Icon(painterResource(DowntifyIcons.ChevronRight), contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDialogs(dialog: TvSettingsDialog?, state: SettingsUiState, actions: SettingsActions, onDismiss: () -> Unit) {
    val settings = state.settings
    when (dialog) {
        TvSettingsDialog.WifiQuality -> TvChoiceDialog(
            title = stringResource(R.string.settings_quality_wifi),
            options = state.qualityOptions,
            selected = settings.wifiQuality,
            label = { qualityLabel(it) },
            onSelect = actions.onWifiQuality,
            onDismiss = onDismiss,
        )

        TvSettingsDialog.NormalizationLevel -> TvChoiceDialog(
            title = stringResource(R.string.settings_normalization_level),
            options = NormalizationLevel.entries,
            selected = settings.normalizationLevel,
            label = { stringResource(it.label) },
            onSelect = actions.onNormalizationLevel,
            onDismiss = onDismiss,
        )

        TvSettingsDialog.ReadAhead -> TvChoiceDialog(
            title = stringResource(R.string.settings_read_ahead),
            options = listOf(0, 1, 2, 3),
            selected = settings.readAhead.tracks,
            label = { readAheadLabel(it) },
            onSelect = actions.onReadAhead,
            onDismiss = onDismiss,
        )

        TvSettingsDialog.StreamCache -> TvChoiceDialog(
            title = stringResource(R.string.settings_stream_cache),
            options = (CacheSizes + settings.readAhead.cacheMb).distinct().sorted(),
            selected = settings.readAhead.cacheMb,
            label = { stringResource(R.string.settings_stream_cache_size, it) },
            onSelect = actions.onStreamCacheMb,
            onDismiss = onDismiss,
        )

        TvSettingsDialog.Theme -> TvChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
            selected = settings.theme,
            label = { stringResource(it.label) },
            onSelect = actions.onTheme,
            onDismiss = onDismiss,
        )

        TvSettingsDialog.Font -> TvChoiceDialog(
            title = stringResource(R.string.settings_font),
            options = AppFont.entries,
            selected = settings.font,
            label = { stringResource(it.label) },
            labelFont = { it.previewFamily },
            onSelect = actions.onFont,
            onDismiss = onDismiss,
        )

        // With no certificate the system's picker opens straight away; with one, change or remove it.
        TvSettingsDialog.ClientCertificate -> if (state.clientCertificate == null) {
            LaunchedEffect(Unit) {
                onDismiss()
                actions.onSelectClientCertificate()
            }
        } else {
            TvConfirmDialog(
                title = stringResource(R.string.settings_client_certificate),
                body = stringResource(R.string.settings_client_certificate_active, state.clientCertificate),
                first = stringResource(R.string.settings_client_certificate_change) to {
                    onDismiss()
                    actions.onSelectClientCertificate()
                },
                second = stringResource(R.string.settings_client_certificate_remove) to {
                    onDismiss()
                    actions.onClearClientCertificate()
                },
                onDismiss = onDismiss,
            )
        }

        TvSettingsDialog.Unpair -> TvConfirmDialog(
            title = stringResource(R.string.settings_unpair_title),
            body = stringResource(R.string.settings_unpair_message),
            first = stringResource(R.string.action_cancel) to onDismiss,
            second = stringResource(R.string.settings_unpair_confirm) to {
                onDismiss()
                actions.onUnpair()
            },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

/** One of a few options, as a short list over the screen; focus starts on the current one. */
@Composable
internal fun <T> TvChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    labelFont: (T) -> FontFamily? = { null },
) {
    Dialog(onDismissRequest = onDismiss) {
        val selectedFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { selectedFocus.requestFocusWhenPlaced() }
        DialogSurface {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = TvRingSpace, bottom = Spacing.sm).semantics { heading() },
            )
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                options.forEach { option ->
                    ChoiceRow(
                        label = label(option),
                        selected = option == selected,
                        font = labelFont(option),
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                        modifier = if (option == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, font: FontFamily?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusFrame(focused, RowRingShape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .background(if (focused) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh, RowShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = font,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(DowntifyIcons.Check),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(22.dp).alpha(if (selected) 1f else 0f),
        )
    }
}

/** A question with two answers; focus starts on the [first], the safe one. */
@Composable
internal fun TvConfirmDialog(
    title: String,
    body: String,
    first: Pair<String, () -> Unit>,
    second: Pair<String, () -> Unit>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.requestFocusWhenPlaced() }
        DialogSurface {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                TvDialogButton(first.first, first.second, Modifier.focusRequester(firstFocus))
                TvDialogButton(second.first, second.second)
            }
        }
    }
}

@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(min = 360.dp, max = 520.dp),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content() }
    }
}

private val NormalizationLevel.label: Int
    get() = when (this) {
        NormalizationLevel.Normal -> R.string.settings_normalization_normal
        NormalizationLevel.Louder -> R.string.settings_normalization_louder
        NormalizationLevel.Maximum -> R.string.settings_normalization_maximum
    }

/** The cache sizes offered on a TV, in MB (the phone's slider goes 10 to 200 in steps of 10). */
private val CacheSizes = listOf(10, 25, 50, 100, 150, 200)
private val SectionsWidth = 260.dp
private val KeepVisible = 64.dp
private val IconCircle = 40.dp
private val RowShape = RoundedCornerShape(12.dp)
private val RowRingShape = RoundedCornerShape(12.dp + TvRingSpace)

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvSettingsPreview() {
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvSettingsScreen(
                state = SettingsUiState(
                    serverName = "nas.local",
                    username = "admin",
                    address = "192.168.1.20:8000",
                    version = "3.2.0",
                    connection = ConnectionState.Connected,
                ),
                actions = SettingsActions(),
            )
        }
    }
}
