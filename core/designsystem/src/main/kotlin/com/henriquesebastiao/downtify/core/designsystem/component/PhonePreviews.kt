package com.henriquesebastiao.downtify.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview

/**
 * Light and dark on the narrowest phone the design is drawn for (360 dp) and a common wide one
 * (412 dp): where values must not break and rows must not crowd.
 */
@Preview(name = "Light · 360 dp", widthDp = 360, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Dark · 360 dp", widthDp = 360, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Light · 412 dp", widthDp = 412, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Dark · 412 dp", widthDp = 412, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class PhonePreviews
