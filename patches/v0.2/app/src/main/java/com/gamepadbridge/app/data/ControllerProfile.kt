package com.gamepadbridge.app.data

import android.view.KeyEvent

enum class XboxInput(val label: String, val isAxis: Boolean = false) {
    A("A"), B("B"), X("X"), Y("Y"),
    L1("LB"), R1("RB"),
    L3("Clique do analógico esquerdo"), R3("Clique do analógico direito"),
    START("Start"), BACK("Select / Back"),
    DPAD_UP("D-Pad Cima"), DPAD_DOWN("D-Pad Baixo"), DPAD_LEFT("D-Pad Esquerda"), DPAD_RIGHT("D-Pad Direita"),
    L2("Gatilho Esquerdo (LT)", isAxis = true),
    R2("Gatilho Direito (RT)", isAxis = true),
    LS_X("Analógico Esquerdo — Eixo X", isAxis = true),
    LS_Y("Analógico Esquerdo — Eixo Y", isAxis = true),
    RS_X("Analógico Direito — Eixo X", isAxis = true),
    RS_Y("Analógico Direito — Eixo Y", isAxis = true);

    companion object {
        val mappingOrder = listOf(A, B, X, Y, L1, R1, L3, R3, START, BACK,
            DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, L2, R2, LS_X, LS_Y, RS_X, RS_Y)
    }
}

sealed class RawSource {
    data class Key(val code: String) : RawSource()
    data class Axis(val code: String, val positiveDirection: Boolean) : RawSource()

    fun serialize(): String = when (this) {
        is Key -> "KEY:$code"
        is Axis -> "AXIS:$code:${if (positiveDirection) "+" else "-"}"
    }

    companion object {
        fun deserialize(s: String): RawSource {
            val parts = s.split(":")
            return when (parts[0]) {
                "KEY" -> Key(parts[1])
                else -> Axis(parts[1], parts[2] == "+")
            }
        }
    }
}

data class ControllerProfile(
    val deviceName: String,
    val mapping: Map<XboxInput, RawSource>
) {
    fun serialize(): String = "$deviceName\n" +
        mapping.entries.joinToString("\n") { "${it.key.name}=${it.value.serialize()}" }

    companion object {
        fun deserialize(text: String): ControllerProfile? {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            val deviceName = lines[0]
            val map = mutableMapOf<XboxInput, RawSource>()
            for (line in lines.drop(1)) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) runCatching {
                    map[XboxInput.valueOf(parts[0])] = RawSource.deserialize(parts[1])
                }
            }
            return ControllerProfile(deviceName, map)
        }

        fun outputKeyCode(input: XboxInput): Int? = when (input) {
            XboxInput.A -> KeyEvent.KEYCODE_BUTTON_A
            XboxInput.B -> KeyEvent.KEYCODE_BUTTON_B
            XboxInput.X -> KeyEvent.KEYCODE_BUTTON_X
            XboxInput.Y -> KeyEvent.KEYCODE_BUTTON_Y
            XboxInput.L1 -> KeyEvent.KEYCODE_BUTTON_L1
            XboxInput.R1 -> KeyEvent.KEYCODE_BUTTON_R1
            XboxInput.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
            XboxInput.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
            XboxInput.START -> KeyEvent.KEYCODE_BUTTON_START
            XboxInput.BACK -> KeyEvent.KEYCODE_BUTTON_SELECT
            XboxInput.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
            XboxInput.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            XboxInput.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            XboxInput.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        }

        fun outputAxis(input: XboxInput): Int? = when (input) {
            XboxInput.LS_X -> android.view.MotionEvent.AXIS_X
            XboxInput.LS_Y -> android.view.MotionEvent.AXIS_Y
            XboxInput.RS_X -> android.view.MotionEvent.AXIS_Z
            XboxInput.RS_Y -> android.view.MotionEvent.AXIS_RZ
            XboxInput.L2 -> android.view.MotionEvent.AXIS_LTRIGGER
            XboxInput.R2 -> android.view.MotionEvent.AXIS_RTRIGGER
            else -> null
        }
    }
}
