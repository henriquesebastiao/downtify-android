package com.henriquesebastiao.downtify.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in physical-device measurements. Pair normally before running; no data reset or playback. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun coldHome() = startup(StartupMode.COLD)

    @Test fun warmHome() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) {
        benchmark.measureRepeated(
            packageName = "com.henriquesebastiao.downtify.debug",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable,
                warmupIterations = 3,
            ),
            startupMode = mode,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            assertTrue(
                "The paired library must become usable",
                device.wait(
                    Until.hasObject(By.text(Pattern.compile("Accueil|Home", Pattern.CASE_INSENSITIVE))),
                    5000,
                ),
            )
        }
    }
}
