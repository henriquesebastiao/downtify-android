package com.henriquesebastiao.downtify.core.player

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDjSettingsTest {
    @Test fun `mode and duration never overwrite the voluntary rhythmic engine preference`() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("local-dj-beta-v1", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = LocalDjSettings(context)
        assertFalse(settings.device.value)
        settings.setMode(LocalDjMode.Transitions)
        settings.setArmed(true)
        assertTrue(settings.armed.value)
        settings.chooseDevice(true)
        assertTrue(settings.armed.value)
        settings.setMode(LocalDjMode.Dj)
        assertTrue(settings.device.value)
        assertFalse(settings.armed.value)
        settings.setTransitionSeconds(99)
        assertEquals(12, settings.transitionSeconds.value)
        val restored = LocalDjSettings(context)
        assertTrue(restored.device.value)
        assertEquals(LocalDjMode.Dj, restored.mode.value)
        assertFalse(restored.armed.value)
    }
}
