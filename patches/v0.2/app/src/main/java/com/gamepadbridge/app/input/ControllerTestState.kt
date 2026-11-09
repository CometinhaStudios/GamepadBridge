package com.gamepadbridge.app.input

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.gamepadbridge.app.data.XboxInput

object ControllerTestState {
    @Volatile var enabled = false
    val pressed = mutableStateMapOf<XboxInput, Boolean>()
    var leftX by mutableFloatStateOf(0f); private set
    var leftY by mutableFloatStateOf(0f); private set
    var rightX by mutableFloatStateOf(0f); private set
    var rightY by mutableFloatStateOf(0f); private set
    var lt by mutableFloatStateOf(0f); private set
    var rt by mutableFloatStateOf(0f); private set

    fun key(code: Int, down: Boolean) {
        val input = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> XboxInput.A
            KeyEvent.KEYCODE_BUTTON_B -> XboxInput.B
            KeyEvent.KEYCODE_BUTTON_X -> XboxInput.X
            KeyEvent.KEYCODE_BUTTON_Y -> XboxInput.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> XboxInput.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> XboxInput.R1
            KeyEvent.KEYCODE_BUTTON_THUMBL -> XboxInput.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> XboxInput.R3
            KeyEvent.KEYCODE_BUTTON_START -> XboxInput.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> XboxInput.BACK
            KeyEvent.KEYCODE_DPAD_UP -> XboxInput.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> XboxInput.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> XboxInput.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> XboxInput.DPAD_RIGHT
            else -> null
        } ?: return
        if (down) pressed[input] = true else pressed.remove(input)
    }

    fun axes(x: Float, y: Float, z: Float, rz: Float, leftTrigger: Float, rightTrigger: Float) {
        leftX = x.coerceIn(-1f, 1f); leftY = y.coerceIn(-1f, 1f)
        rightX = z.coerceIn(-1f, 1f); rightY = rz.coerceIn(-1f, 1f)
        lt = leftTrigger.coerceIn(0f, 1f); rt = rightTrigger.coerceIn(0f, 1f)
    }
}
