package com.henriquesebastiao.downtify.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the screen's bottom the mini player and the floating bar cover (the system's
 * navigation bar under them included), 0 when neither shows. The content runs on behind them, as
 * in PixelPlayer, so a scrolling screen pads its end by this ([plusBottomBars]): its last item can
 * still scroll clear of them.
 */
val LocalBottomBarsInset = compositionLocalOf { 0.dp }

/** [this] with its bottom grown by what the mini player and the floating bar cover. */
@Composable
fun PaddingValues.plusBottomBars(): PaddingValues {
    val covered: Dp = LocalBottomBarsInset.current
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + covered,
    )
}
