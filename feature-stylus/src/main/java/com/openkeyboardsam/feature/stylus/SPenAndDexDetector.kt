package com.openkeyboardsam.feature.stylus

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Detects Galaxy S22 Ultra S Pen events, Samsung DeX / external desktop displays,
 * and physical hardware keyboards using standard Android APIs.
 */
object SPenAndDexDetector {

    fun isStylusEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    fun isStylusPrimaryButtonPressed(event: MotionEvent): Boolean {
        return (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
    }

    fun isDesktopOrDexMode(context: Context): Boolean {
        val config = context.resources.configuration
        val isLargeWide = config.screenWidthDp >= 900 &&
            (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK
        if (isLargeWide) return true

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        return dm.displays.any { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
    }

    fun hasHardwareKeyboard(context: Context): Boolean {
        val config = context.resources.configuration
        if (config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        ) {
            return true
        }
        return InputDevice.getDeviceIds().any { id ->
            val dev = InputDevice.getDevice(id) ?: return@any false
            !dev.isVirtual && (dev.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }
    }
}
