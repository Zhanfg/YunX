package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveConcurrencyControllerTest {
    @Test
    fun healthyWindowsRampGraduallyToLimit() {
        val controller = AdaptiveConcurrencyController(
            requestedThreads = 128,
            providerLimit = 64,
            initialWorkers = 16
        )
        assertEquals(16, controller.currentWorkers)
        controller.onHealthyWindow()
        assertEquals(16, controller.currentWorkers)
        controller.onHealthyWindow()
        assertEquals(24, controller.currentWorkers)
        controller.onHealthyWindow()
        controller.onHealthyWindow()
        assertEquals(36, controller.currentWorkers)
    }

    @Test
    fun pressureImmediatelyBacksOff() {
        val controller = AdaptiveConcurrencyController(128, initialWorkers = 64)
        assertEquals(32, controller.onPressure(ConcurrencyPressure.RATE_LIMITED))
        assertEquals(16, controller.onPressure(ConcurrencyPressure.RANGE_IGNORED))
        assertEquals(12, controller.onPressure(ConcurrencyPressure.TRANSIENT_NETWORK))
        assertEquals(3, controller.pressureEvents)
    }

    @Test
    fun providerCapCannotBeExceeded() {
        val controller = AdaptiveConcurrencyController(128, providerLimit = 8, initialWorkers = 8)
        repeat(8) { controller.onHealthyWindow(requiredWindows = 1) }
        assertEquals(8, controller.currentWorkers)
    }
}
