package fr.prisme.player.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the paths that matter on a slow TV box: cold start, Home, scrolling the rails,
 * opening a channel and coming back.
 *
 * The device must already have a source configured (Xtream or M3U); otherwise the journey
 * stops at the welcome screen. See docs/BASELINE_PROFILE.md.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation argument")

        rule.collect(
            packageName = targetAppId,
            includeInStartupProfile = true,
        ) {
            val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            // 1. Cold start: splash then Home.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            Thread.sleep(4_000)

            // 2. Scroll through the rails, then come back up.
            repeat(3) { ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN); Thread.sleep(500) }
            repeat(4) { ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT); Thread.sleep(400) }
            repeat(3) { ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP); Thread.sleep(500) }

            // 3. Open a channel (player), let it initialise, then go back.
            ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(5_000)
            ui.pressBack()
            Thread.sleep(1_500)

            // 4. Top bar: visit Films and Series, then come back to Television.
            repeat(3) { ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP); Thread.sleep(300) }
            repeat(2) {
                ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                Thread.sleep(300)
                ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                Thread.sleep(2_500)
            }
            repeat(2) { ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT); Thread.sleep(300) }
            ui.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(1_500)
        }
    }
}
