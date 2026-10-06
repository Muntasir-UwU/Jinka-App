package com.jinki.app

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.perfetto.capturePerfettoTrace
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.jinki.app",
        metrics = listOf(androidx.benchmark.macro.StartupTimingMetric()),
        iterations = 10,
        startupMode = androidx.benchmark.macro.CompilationMode.Full(
            baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Generate
        ),
        measureBlock = {
            pressHome()
            startActivityAndWait()
        }
    )

    @Test
    fun searchAndPlay() = benchmarkRule.measureRepeated(
        packageName = "com.jinki.app",
        metrics = listOf(androidx.benchmark.macro.StartupTimingMetric()),
        iterations = 10,
        startupMode = androidx.benchmark.macro.CompilationMode.Full(
            baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Generate
        ),
        measureBlock = {
            pressHome()
            startActivityAndWait()
            
            composeRule.onNodeWithContentDescription("Search").performClick()
            composeRule.onNodeWithText("Search").performTextInput("test song")
            composeRule.onNodeWithText("test song").performClick()
        }
    )

    private fun pressHome() {
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.pressHome()
    }

    private fun startActivityAndWait() {
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        val intent = android.content.Intent().apply {
            setPackage("com.jinki.app")
            setAction(android.content.Intent.ACTION_MAIN)
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        device.executeShellCommand("am start -W -S com.jinki.app/.MainActivity")
    }
}