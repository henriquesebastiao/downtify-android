package com.henriquesebastiao.downtify.ui.common

import androidx.compose.runtime.compositionLocalOf

/** Every feature opens the existing activity-scoped player rather than creating a second one. */
val LocalOpenPlayer = compositionLocalOf<() -> Unit> { {} }
