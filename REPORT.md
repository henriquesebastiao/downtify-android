# search-lyrics

Slice H of `/root/work/PLAN.md`, in `/root/work/wt-search-lyrics` on `feat/search-lyrics`.

**Bottom line:** Part 1 (search filters) is new work. **Part 2 (lyrics offset) was already on this
branch before I started** — commit `4f36ea9 feat: move a song's synced lyrics earlier or later`,
reached from `main` — so I verified it against the spec instead of writing it again, and changed
nothing there. Details in [Part 2](#part-2--lyrics-offset-already-on-the-branch-verified-not-rewritten).

## What it does (user-visible, and where it appears in the UI)

**Search tab → the row of chips under the search field.** Once the server has returned a page with
more than one song for the words typed, a second chip row appears under the All / Songs / Albums /
Artists / Playlists row:

- **Filters** (with a chevron) opens three rows of chips: **Artist**, **Album**, **Year**, each
  starting with **Any**. The values are the ones the server's page actually contains, so a chip can
  never lead to an empty page on its own.
- **Sort: <order>** — a chip that opens a menu: *Relevance* (the default: the server's own
  ranking), *Title*, *Artist*, *Length* (shortest first).
- **Clear filters** appears as soon as anything is set and puts every field back to **Any** and the
  order back to *Relevance*.

Narrowing hides the server's song rows *and* its album cards, and says so: if the chips hide every
row of a page that was not empty, the section reads "Nothing on the server's page matches these
filters" instead of "The server found nothing more for …". Playing a row queues the rows as
*narrowed and ordered* (the list on screen), because `RemoteActions.onPlay` receives the same list.

Changing the words typed clears Artist/Album/Year (they were values of the old page) but keeps the
chosen order; the order is a preference, the fields are a reading of one page.

**This narrows the result page, not the server's index.** The server's search takes the words and
nothing else — `search_endpoint(query: str = Query(''))` in `/root/work/downtify-server/downtify/api.py:2891`,
and the app's client mirrors that exactly, `WebApi.searchSongs(@Query("query") query: String)`
(`core/network/.../WebApi.kt:42`). There is no field syntax to pass through and inventing one would
be silently ignored, so nothing is sent: the page (≤ 20 songs, `providers.search_songs(query, limit=20)`),
comes back whole and is filtered and sorted in `:core:model`. A song the server left off its page
cannot be reached by a chip. Filtering the *phone's* library results was not needed — the words
already narrow them (`SearchViewModel.search`).

## Files changed

| path | what |
|---|---|
| `core/model/src/main/kotlin/.../core/model/ResultFilters.kt` | **new.** `ResultFilters` (artist/album/year + order), `ResultSort`, `ResultFilterOptions`, `ResultFiltering.options/apply/albums` — pure, no Android |
| `core/model/src/test/kotlin/.../core/model/ResultFiltersTest.kt` | **new.** unit tests for the above |
| `app/src/main/kotlin/.../feature/search/ResultFiltersBar.kt` | **new.** the chip bar: `ResultFiltersBar`, `FiltersChip`, `SortChip`, `ClearChip`, `FacetRow`, `FacetChip`, `@PhonePreviews` |
| `app/src/main/kotlin/.../feature/search/SearchScreen.kt` | shows the bar under the kind row; `onFiltersChange` parameter; the "chips hid everything" line |
| `app/src/main/kotlin/.../feature/search/SearchViewModel.kt` | chip state in `SavedStateHandle`, `filters`/`filterOptions` in `SearchUiState`, `Found.page` (raw page) vs `Found.songs` (narrowed), `onFiltersChange`, fields cleared on a new query |
| `app/src/test/kotlin/.../feature/search/ResultFiltersTest.kt` | **new.** Robolectric tests of the bar through the stateless `SearchScreen` |
| `handoff/search-lyrics.md` | **new.** the eight `s_*` strings the parent must add (only shared file touched) |
| `REPORT.md` | this file |

Part 2: no files changed (already present, see below).

## Evidence that the APIs I call exist

Every symbol below was grepped in this worktree; nothing outside `:core:model` and the app's search
feature is invented.

New code is built only from these:

- `- symbol -> file:line`
- `RemoteSong(id,title,artists,album,coverUrl,durationSeconds,url,source,previewUrl,year,raw)` -> `core/model/.../Catalog.kt:31` (fields `artists` :34, `album` :35, `year` :42, `durationSeconds` :37; `artist` :45)
- `RemoteAlbum(id,title,artist,coverUrl,year,url,releaseType)` -> `core/model/.../Catalog.kt:49`
- `CatalogSource.YouTube` -> `core/model/.../Catalog.kt` (`enum class CatalogSource`), used the same way in `app/src/test/.../search/SearchServerTest.kt:44`
- `SearchFilter` (the kind tabs, **not** my type — mine is `ResultFilters` to keep them apart) -> `feature/search/SearchViewModel.kt:53`
- `ServerResults.Found` -> `SearchViewModel.kt:79`; `SearchUiState` -> `SearchViewModel.kt:88`
- `CatalogRepository.search(query)` -> `core/data/.../catalog/CatalogRepository.kt:42`
- 4-arity `kotlinx.coroutines.flow.combine(a,b,c,d){}` -> already used by this file, `SearchViewModel.kt:181` (`local`)
- `SavedStateHandle.getStateFlow/get/set` -> already used, `SearchViewModel.kt:137-141`, `onQueryChange`
- `FilterChip`, `FilterChipDefaults.IconSize` -> used in this app: `feature/collection/CollectionScreen.kt:427` and `:445`
- `DropdownMenu` / `DropdownMenuItem(leadingIcon=…)` -> `feature/search/RemoteItems.kt:153-154`
- `AnimatedVisibility(enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(...))` -> `feature/player/LyricsScreen.kt:157-163`
- `LazyRow`, `items(list, key=…)` -> `feature/search/SearchScreen.kt:223,293`
- `rememberSaveable { mutableStateOf(...) }` -> `feature/home/HomeScreen.kt:326`
- `Spacing.screen/sm/xs/md`, `CircleShape`, `Modifier.heightIn(min = 40.dp)` chips -> `core/designsystem/.../theme/Shape.kt:154-162`; the 40 dp chip in `CollectionScreen.kt:439-452`
- `DowntifyIcons.Sort/Check/Close/Collapse/ChevronRight` -> `core/designsystem/.../component/DowntifyIcons.kt:85,81,83,51,122`
- `@PhonePreviews` -> `core/designsystem/.../component/PhonePreviews.kt:16`
- `stringResource`, `stringResource(id, arg)` -> used throughout, e.g. `SearchScreen.kt:285` (with args)
- Robolectric screen tests via `createComposeRule()` on the stateless screen -> `app/src/test/.../search/SearchServerTest.kt:26-53`
- Pure-model tests, JUnit 4 -> `core/model/src/test/.../TextSearchTest.kt:1-14`

## Assumptions I could not verify

No Android SDK here, so **nothing in this slice has been compiled or run** (`./gradlew` is forbidden
by PLAN §0). These are the assumptions a build will settle:

1. **`FilterChip(trailingIcon = …)`** — I use it for the Filters chevron. `leadingIcon` is used in
   this repo (`CollectionScreen.kt`), `trailingIcon` is not; it is part of Material 3's `FilterChip`
   alongside `leadingIcon`, and the project pins material3 1.5.0-beta01.
2. **Robolectric text selectors assume the chips are on screen at 411 dp.** `ResultFiltersTest`
   finds chips by their text ("Ivo Marsh", "Static Bloom", "2019", "Clear filters"), which requires
   those chips to be composed inside their `LazyRow` and their centre to be inside the viewport —
   longer labels in a translation (or a narrower test device) would need a scroll first. The test
   data is deliberately short and the filtered values are the 2nd/3rd chip of their row; the top
   row is Filters + Sort + Clear ≈ 480 dp wide at `w411dp`, so Clear's centre is still on screen.
3. **`Modifier.semantics` inside `FilterChip`'s modifier chain** — I avoided it (I first wrote a
   content description for the sort chip and dropped it) because the merge behaviour there is
   unverifiable without a build; the chips are found by text instead.
4. **The `s_*` strings do not exist yet.** `:app` cannot compile until the parent applies
   `handoff/search-lyrics.md`. That is by design (PLAN §4), but it means my compile order is
   unverified too.
5. **Robolectric `setContent` is called once per test** — my `show()` helper is called once per
   test and wires the callback back into a `mutableStateOf`, so the screen re-renders as the
   ViewModel would. That pattern is mine; the existing tests only render static state.
6. **`SearchUiState.filters`/`filterOptions` new fields** default to `ResultFilters()` /
   `ResultFilterOptions()`, so the existing `SearchServerTest` calls compile unchanged (they use
   named arguments and `Found(songs, albums)` positionally — the new `page` parameter has a default).

## What the parent must do (handoff items, DI, routes, strings)

`handoff/search-lyrics.md` has the exact anchor and snippet. In short:

- **`app/src/main/res/values/strings.xml` + the 7 translations**: insert 9 `s_*` keys after
  `<string name="search_kind_playlist">Playlist</string>` (English values there):
  `s_filters`, `s_filters_any`, `s_filters_clear`, `s_filter_nothing`, `s_filter_year`, `s_sort`
  (`Sort: %1$s`), `s_sort_relevance`, `s_sort_title`, `s_sort_duration`.
- Reused **existing** keys, nothing to add: `search_kind_artist`, `search_kind_album`.
- **Nothing else**: no route (`SearchRoute` unchanged), no DI binding (`SearchViewModel`'s
  constructor is untouched), no manifest, no gradle, no new dependency, no `Routes.kt` entry.

## Test coverage added (file -> what it asserts)

- `core/model/src/test/kotlin/.../ResultFiltersTest.kt` -> `ResultFiltering`:
  - `the page offers each artist, album and year once` — a duet counts for both artists; years
    newest first; a song without a year adds nothing.
  - `an artist, an album and a year narrow the page` — each field alone, two together, ignoring
    case, and the empty cases.
  - `relevance keeps the server's ranking, the others order the narrowed rows` — Title / Artist /
    Duration orders (duets sort by the joined artist), ties keep the page's order, sorting alone
    doesn't drop rows, and `Relevance` returns the list untouched.
  - `album cards follow the chips too` — the album half of the filters.
  - `clear means nothing set` — `hasFields` vs `isActive`.
- `app/src/test/kotlin/.../ResultFiltersTest.kt` (Robolectric, `w411dp-h914dp`) -> the bar inside
  `SearchScreen`:
  - `anArtistChipNarrowsThePageAndClearPutsItBack` — Filters opens the rows, an artist chip emits
    `ResultFilters(artist = …)` and the other artist's row disappears, Clear filters emits
    `ResultFilters()` and the row comes back. Also: no Clear chip before anything is set.
  - `anAlbumAndAYearNarrowTogether` — album then year accumulate, the row set shrinks, Clear resets.
  - `theOrderIsItsOwnChipWithAMenu` — the menu emits `ResultFilters(sort = Duration)` and the chip
    relabels to "Sort: Length".
  - `oneRowIsNothingToNarrowOrOrder` — a one-song page shows no chips at all.

PART 2's existing coverage (unchanged by me): `core/model/LrcParserTest.kt:60-80` (shift/clamp/
`seconds` formatting), `core/data/.../library/LyricsOffsetTest.kt` (per-track offsets add up under
concurrent presses, clamp, reset, and the 4→5 migration keeps `offline_collections`),
`app/src/test/.../player/LyricsScreenTest.kt:64-118` (the timing bar's buttons, the offset label,
the tapped line seeking to the shifted time, disabled Reset at 0, no timing control for static
lyrics).

## Part 2 — lyrics offset (already on the branch; verified, not rewritten)

`git log` shows these on the branch before I touched anything, and `git diff main...HEAD` was empty
when I started:

- arithmetic, steps of 250 ms, ±15 s clamp, `seconds()` in the locale's decimal mark:
  `core/model/.../Lyrics.kt:25-49`; the current line recomputed over the shifted list by
  `LrcParser.activeIndex` (`Lyrics.kt:78`), called with the shifted lines in
  `feature/player/LyricsScreen.kt:183-186`.
- the control: a timing button in the lyrics top bar (`LyricsScreen.kt:212-250`), the
  earlier/later/reset bar with the signed offset label (`LyricsScreen.kt:256-291`), shown only for
  synced lyrics.
- per-track persistence: `LyricsOffsetEntity(trackId, offsetMs)` (`core/data/.../db/Entities.kt:80`),
  `LyricsOffsetDao` upsert/delete-by-track (`db/Daos.kt:83-86`), read/write in one transaction so
  quick presses add up (`LibraryRepository.kt:167-181`), Room `autoMigrations` 4→5 with the exported
  `core/data/schemas/.../5.json` and a hand-written test — no destructive fallback
  (`db/DowntifyDatabase.kt:19-29`).
- the ViewModel exposes the *playing/loaded* track's offset (`PlayerViewModel.kt:95-99`), shifts and
  resets it (`:147-156`), and the screen collects it (`ui/DowntifyApp.kt:454,566`).
- all five `player_lyrics_timing*` / `player_lyrics_offset` strings exist in `values` and the seven
  translations (`strings.xml:390-395`, 35 hits across `values-*`).

It satisfies the four requirements (0.25 s nudges, per-track id, survives restart via Room, the
current line follows because the lines are shifted before `activeIndex`), and negative shifted times
can't seek below 0 (`LyricsScreen.kt:427`). **I changed none of it.** If the parent wanted this slice
to *author* that code, it already exists upstream — treat Part 2 as verified rather than delivered.

## Known gaps

- **Nothing was compiled or run** (no SDK; PLAN §0). The tests are written to the repo's conventions
  but their first real run is the parent's.
- The chips act on the server's page and its album cards only. The **phone's library** rows are not
  filtered by them (the words already narrow those), and a **pasted Spotify/YouTube link** gets no
  chips (its tracks come from `/api/url/resolve`, there is no page).
- Album cards keep the server's order: there is no album sort (they have no duration).
- Editing the query clears the field chips but keeps the order — deliberate, documented above; if
  the parent prefers sticky fields, it is one `if` in `SearchViewModel.onQueryChange`.
- No ViewModel test: the app module has no VM test harness (existing feature tests are screen
  tests), so the `SavedStateHandle` plumbing (keys `filterArtist/Album/Year/Sort`, clearing on a new
  query) is covered only by reading, not by a test.
- The `s_*` strings are missing until the parent applies the handoff, as PLAN §4 requires.
