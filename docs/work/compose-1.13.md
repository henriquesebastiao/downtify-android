# Compose 1.13 and the NaN corner crash

Two crashes from the 0.3.0 reports are AndroidX bugs, not app logic. Both are fixed here.

## `IllegalArgumentException: LayoutNode N not found in RectList`

`androidx.compose.ui.spatial.RectManager` throws when a node is placed while an alignment line is
being queried during measurement — `alignByBaseline()`, `AnimatedContent`, a lazy list measuring and
placing in the same pass.

- 0.3.0 ships `composeBom 2026.09.00` → `compose-ui 1.12.1`, whose
  `LayoutNodeAlignmentLines` calls `childOwner.layoutChildren()` without propagating
  `isPlacingForAlignment`.
- `compose-ui 1.13.0` propagates it (Google issue 549552303; release note in 1.13.0-beta01). Diffed
  between the two `-sources.jar` artifacts, not taken from the release note.

Fix: `composeBom 2026.11.01` (ui/foundation 1.13.0), `material3 1.5.0` stable — `SegmentedListItem`,
`IconButtonShapes`, `LoadingIndicator` and `ListItemDefaults.segmentedShapes` all still exist there,
so the Expressive screens keep compiling.

`compose-ui 1.13.0` requires compiling against API **37.1**, and Android publishes minor levels now:
`compileSdk = 37` resolves to `android-37.0` and `checkDebugAarMetadata` refuses it. Hence
`compileSdkMinor = 1` in `build-logic/.../KotlinAndroid.kt` (and `platforms;android-37.1` installed
in the NAS builder).

## `IllegalArgumentException: Corner size in Px can't be negative(topStart = NaN, …)`

`CornerBasedShape.createOutline` throws while a frame draws, with all four corners NaN. In
`ExpandingPlayer`'s `flying` modifier the cover's corner is `radius / scale` where
`scale = rect.width / placeable.width`: the base size is coerced to `>= 1` but the rect is not, so a
zero-width frame divides 0 by 0 and a single-value `RoundedCornerShape(NaN)` resolves every corner to
NaN.

Fix: `pxRoundedShape(topStart, topEnd, bottomEnd, bottomStart)` in
`core/designsystem/.../theme/Shape.kt` returns `RectangleShape` when a corner is NaN, infinite or
negative, and `ExpandingPlayer` sends both layer shapes through it. One frame loses its rounding
instead of crashing the app. `PxRoundedShapeTest` pins the behaviour.

Detekt's `ComplexCondition` counts four terms as too complex, so the guard is written as two pairs of
early returns rather than one four-part condition.

## Verification

- `spotlessCheck detekt assembleBeta -PabiSplits testDebugUnitTest` — green on the NAS builder.
- `PxRoundedShapeTest`: 5/5.
- APK: `aapt2 dump badging` + `apksigner verify` + sha256 checked through
  `https://music.slogiker.si/beta/<file>`.
- Not verified: the `RectList` crash cannot be reproduced on demand, so 1.13.0 is confirmed as the
  version that carries the fix, not as "the crash stopped happening".

## Building without clobbering the other session

`android-builder`'s `/src` is shared. Two sessions extracting over it produce a tree that is neither
copy (a test file with no matching source, stale theme files), and the build then fails on code that
does not exist in either tree. Extract into a per-session directory (`/src2`, `/src3`) and `grep` a
marker from your own change before trusting any result.
