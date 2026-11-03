package com.henriquesebastiao.downtify.feature.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SectionCard
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.LicensePart
import com.henriquesebastiao.downtify.core.model.LicenseText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class LicenseItem(@StringRes val title: Int, @StringRes val body: Int)

/** A group of the summary: what the license allows, asks or rules out, under its own mark. */
private class LicenseGroup(@StringRes val title: Int, @DrawableRes val icon: Int, val items: List<LicenseItem>)

private val permissions = listOf(
    LicenseItem(R.string.license_permission_commercial, R.string.license_permission_commercial_body),
    LicenseItem(R.string.license_permission_modification, R.string.license_permission_modification_body),
    LicenseItem(R.string.license_permission_distribution, R.string.license_permission_distribution_body),
    LicenseItem(R.string.license_permission_patent, R.string.license_permission_patent_body),
    LicenseItem(R.string.license_permission_private, R.string.license_permission_private_body),
)
private val conditions = listOf(
    LicenseItem(R.string.license_condition_notice, R.string.license_condition_notice_body),
    LicenseItem(R.string.license_condition_changes, R.string.license_condition_changes_body),
    LicenseItem(R.string.license_condition_source, R.string.license_condition_source_body),
    LicenseItem(R.string.license_condition_same, R.string.license_condition_same_body),
)
private val limitations = listOf(
    LicenseItem(R.string.license_limitation_liability, R.string.license_limitation_liability_body),
    LicenseItem(R.string.license_limitation_warranty, R.string.license_limitation_warranty_body),
)

/** The text in `res/raw/license.txt` split into headings and paragraphs, or why there is none yet. */
private sealed interface LicenseLoad {
    data object Loading : LicenseLoad

    data object Failed : LicenseLoad

    data class Loaded(val parts: List<LicensePart>) : LicenseLoad
}

@Composable
fun LicenseRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val load by produceState<LicenseLoad>(LicenseLoad.Loading) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
                LicenseLoad.Loaded(LicenseText.parts(raw))
            }.getOrDefault(LicenseLoad.Failed)
        }
    }
    LicenseScreen(
        fullText = (load as? LicenseLoad.Loaded)?.parts,
        failed = load == LicenseLoad.Failed,
        onBack = onBack,
        modifier = modifier,
    )
}

private val summary = listOf(
    LicenseGroup(R.string.license_permissions, DowntifyIcons.CheckCircle, permissions),
    LicenseGroup(R.string.license_conditions, DowntifyIcons.Info, conditions),
    LicenseGroup(R.string.license_limitations, DowntifyIcons.Close, limitations),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicenseScreen(fullText: List<LicensePart>?, failed: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PageHeader(
                title = stringResource(R.string.license_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.license_name),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.license_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(summary) { group -> LicenseSummary(group) }
            item {
                Text(
                    stringResource(R.string.license_full_text),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Spacing.lg).semantics { heading() },
                )
            }
            if (failed) {
                item {
                    Text(
                        stringResource(R.string.license_text_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (fullText != null) {
                items(fullText) { part -> LicensePartText(part) }
            }
        }
    }
}

/** One group of the summary as a section card: its mark and title, then each point under it. */
@Composable
private fun LicenseSummary(group: LicenseGroup) {
    SectionCard(
        icon = group.icon,
        title = stringResource(group.title),
        modifier = Modifier.fillMaxWidth(),
    ) {
        group.items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(item.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicensePartText(part: LicensePart) {
    when (part) {
        is LicensePart.Heading -> Text(
            part.text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = if (part.text.contains('\n')) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
        )

        is LicensePart.Paragraph -> Text(
            part.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PhonePreviews
@Composable
private fun LicensePreview() {
    DowntifyTheme {
        LicenseScreen(
            fullText = listOf(
                LicensePart.Heading("GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007"),
                LicensePart.Heading("0. Definitions."),
                LicensePart.Paragraph("\"This License\" refers to version 3 of the GNU General Public License."),
            ),
            failed = false,
            onBack = {},
        )
    }
}
