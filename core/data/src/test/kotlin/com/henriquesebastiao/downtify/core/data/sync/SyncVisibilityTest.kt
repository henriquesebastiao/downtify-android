package com.henriquesebastiao.downtify.core.data.sync

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncVisibilityTest {
    @Test fun `car service syncs without a phone activity and stops on disconnect`() = runTest {
        val phone = MutableStateFlow(false)
        val car = MutableStateFlow(false)
        syncVisibility(phone, car).test {
            assertFalse(awaitItem())
            car.value = true
            assertTrue(awaitItem())
            phone.value = true
            expectNoEvents()
            car.value = false
            expectNoEvents()
            phone.value = false
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
