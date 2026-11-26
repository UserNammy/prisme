package fr.prisme.player.ui.feedback

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Small, platform-native remote feedback. The no-volume AudioManager API honours Android's
 * system sound-effects preference, so a muted TV or a user who disables UI sounds stays silent.
 */
object UiSoundEffects {
    fun playForRemoteKey(context: Context, keyCode: Int, repeatCount: Int = 0) {
        // A held D-pad key can produce dozens of repeats per second; only acknowledge the
        // initial press so browsing a channel list remains calm.
        if (repeatCount != 0) return

        val effect = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP,
            -> AudioManager.FX_FOCUS_NAVIGATION_UP

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            -> AudioManager.FX_FOCUS_NAVIGATION_DOWN

            KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            -> AudioManager.FX_BACK

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_GUIDE,
            -> AudioManager.FX_KEY_CLICK

            else -> return
        }

        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.playSoundEffect(effect)
    }
}
