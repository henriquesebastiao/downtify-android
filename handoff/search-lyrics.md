# Slice `search-lyrics` — handoff to the parent

Everything below is a change to a **shared file** that only the parent may make. The slice compiles
once these strings exist; until then `R.string.s_*` is unresolved and `:app` will not build.

No other shared file is needed: no new route (`SearchRoute` already exists), no DI binding
(`SearchViewModel`'s constructor is unchanged), no manifest or gradle change, no new dependency.

---

## 1. `app/src/main/res/values/strings.xml` (English) and its 7 translations

Insert this block in **`app/src/main/res/values/strings.xml`**, immediately **after** the anchor
line that already exists:

```xml
    <string name="search_kind_playlist">Playlist</string>
```

The same anchor line (`search_kind_playlist`) exists in every translation file
(`values-bg`, `values-el`, `values-es`, `values-fr`, `values-hu`, `values-pt-rBR`, `values-tr`),
so the block goes in the same place in all eight files.

```xml
    <!-- Search: narrowing the server's page of results -->
    <string name="s_filters">Filters</string>
    <string name="s_filters_any">Any</string>
    <string name="s_filters_clear">Clear filters</string>
    <string name="s_filter_nothing">Nothing on the server\'s page matches these filters.</string>
    <string name="s_filter_year">Year</string>
    <string name="s_sort">Sort: %1$s</string>
    <string name="s_sort_relevance">Relevance</string>
    <string name="s_sort_title">Title</string>
    <string name="s_sort_duration">Length</string>
```

| key | English value (source of truth) | where it shows |
|---|---|---|
| `s_filters` | Filters | the chip that opens the fields (artist / album / year) |
| `s_filters_any` | Any | the first chip of each field's row: that field lets everything through |
| `s_filters_clear` | Clear filters | the chip that resets every field and the order |
| `s_filter_nothing` | Nothing on the server's page matches these filters. | the line under the server header when the chips hid every row of a page that was not empty (note the escaped apostrophe: `server\'s`) |
| `s_filter_year` | Year | heading of the years row |
| `s_sort` | `Sort: %1$s` | the order chip's label, filled with one of the three below |
| `s_sort_relevance` | Relevance | the server's own ranking (the default) |
| `s_sort_title` | Title | rows by title |
| `s_sort_duration` | Length | rows by duration, shortest first |

Notes for the translator:

- `s_sort` **must keep `%1$s`**: it is filled with `s_sort_relevance` / `s_sort_title` /
  `search_kind_artist` / `s_sort_duration`. A word order with the value at the end
  (e.g. "Ordenar por %1$s") is fine.
- The artist row's heading and the *Sort: Artist* option both reuse the **existing**
  `search_kind_artist` ("Artist") and the album row's heading the existing
  `search_kind_album` ("Album") — already translated in all eight locales, nothing to add.

`plurals` needed: none. `translatable="false"` needed: none.
