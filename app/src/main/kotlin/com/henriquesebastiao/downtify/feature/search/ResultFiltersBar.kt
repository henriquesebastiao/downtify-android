package com.henriquesebastiao.downtify.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResultFilterOptions
import com.henriquesebastiao.downtify.core.model.ResultFiltering
import com.henriquesebastiao.downtify.core.model.ResultFilters
import com.henriquesebastiao.downtify.core.model.ResultSort

/**
 * The chips under the search field, for the server's results: [ResultFilters] on one row — the way
 * into the fields, the order of the rows and the way back to everything — and, opened, one row of
 * values per field, offered by [options] (the page the server returned).
 *
 * These narrow that page, not the server's index: its search takes the words and nothing else, so
 * the rows it left out can't be found this way. The screen decides when there is a page worth
 * ordering and shows the bar then.
 */
@Composable
internal fun ResultFiltersBar(
    options: ResultFilterOptions,
    filters: ResultFilters,
    onChange: (ResultFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Open when the page is already narrowed: the fields explain the rows that are missing.
    var expanded by rememberSaveable { mutableStateOf(filters.hasFields) }
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // A page of bare downloads (Soulseek stubs) has no artist, album or year to pick: no way in.
            if (!options.isEmpty) {
                item(key = "fields") {
                    FiltersChip(open = expanded || filters.hasFields) { expanded = !expanded }
                }
            }
            item(key = "sort") {
                SortChip(selected = filters.sort) { onChange(filters.copy(sort = it)) }
            }
            if (filters.isActive) {
                item(key = "clear") {
                    ClearChip { onChange(ResultFilters()) }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded && !options.isEmpty,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FacetRow(
                    title = stringResource(R.string.search_kind_artist),
                    values = options.artists,
                    selected = filters.artist,
                ) { onChange(filters.copy(artist = it)) }
                FacetRow(
                    title = stringResource(R.string.search_kind_album),
                    values = options.albums,
                    selected = filters.album,
                ) { onChange(filters.copy(album = it)) }
                FacetRow(
                    title = stringResource(R.string.s_filter_year),
                    values = options.years,
                    selected = filters.year,
                ) { onChange(filters.copy(year = it)) }
            }
        }
    }
}

/** "Filters", filled while the fields are open or narrowing the page. */
@Composable
private fun FiltersChip(open: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = open,
        onClick = onClick,
        label = { Text(stringResource(R.string.s_filters)) },
        trailingIcon = {
            Icon(
                painterResource(if (open) DowntifyIcons.Collapse else DowntifyIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

/** The order of the rows, as one chip: its menu is the choices, the chip says which one is on. */
@Composable
private fun SortChip(selected: ResultSort, onSelect: (ResultSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.s_sort, stringResource(selected.label))
    Box {
        FilterChip(
            selected = selected != ResultSort.Relevance,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    painterResource(DowntifyIcons.Sort),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            shape = CircleShape,
            modifier = Modifier.heightIn(min = 40.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ResultSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(painterResource(DowntifyIcons.Check), contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** "Clear filters": every field back to "any" and the rows back to the server's ranking. */
@Composable
private fun ClearChip(onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(stringResource(R.string.s_filters_clear)) },
        leadingIcon = {
            Icon(
                painterResource(DowntifyIcons.Close),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

/** One field of the page as a row of chips: "Any" or one of [values]. */
@Composable
private fun FacetRow(
    title: String,
    values: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "any") {
                FacetChip(stringResource(R.string.s_filters_any), selected == null) { onSelect(null) }
            }
            items(values, key = { it }) { value ->
                FacetChip(value, value == selected) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun FacetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painterResource(DowntifyIcons.Check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

private val ResultSort.label: Int
    get() = when (this) {
        ResultSort.Relevance -> R.string.s_sort_relevance
        ResultSort.Title -> R.string.s_sort_title
        // The same word as the artist pages and the library's order.
        ResultSort.Artist -> R.string.search_kind_artist
        ResultSort.Duration -> R.string.s_sort_duration
    }

@PhonePreviews
@Composable
private fun ResultFiltersBarPreview() {
    val songs = listOf(
        RemoteSong(
            id = "a",
            title = "Harbor Song",
            artists = listOf("Nora Vale"),
            album = "Harbor Nights",
            coverUrl = "",
            durationSeconds = 242,
            url = "",
            source = CatalogSource.YouTube,
            previewUrl = "",
            year = "2021",
            raw = "{}",
        ),
        RemoteSong(
            id = "b",
            title = "Lights Out",
            artists = listOf("Kai Sato"),
            album = "Static Bloom",
            coverUrl = "",
            durationSeconds = 190,
            url = "",
            source = CatalogSource.YouTube,
            previewUrl = "",
            year = "2019",
            raw = "{}",
        ),
    )
    DowntifyTheme {
        ResultFiltersBar(
            options = ResultFiltering.options(songs),
            filters = ResultFilters(artist = "Kai Sato"),
            onChange = {},
        )
    }
}
