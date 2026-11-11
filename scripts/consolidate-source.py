from pathlib import Path
import re

root = Path("app/src/main/java/com/gamepadbridge/app")
reader = root / "input/DeviceNodeReader.kt"
s = reader.read_text()

s = s.replace(
    "data class AxisRange(val min: Int, val max: Int) {",
    "data class AxisRange(val min: Int, val max: Int, val currentValue: Int? = null) {",
    1
)
old_regex = r'''val regex = Regex("""(ABS_[A-Z0-9_]+)\s*:\s*value\s+-?\d+,\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")'''
new_regex = r'''val regex = Regex("""(ABS_[A-Z0-9_]+)\s*:\s*value\s+(-?\d+),\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")'''
if old_regex not in s:
    if new_regex not in s:
        raise SystemExit("Axis range regex not found; refusing unsafe rewrite")
else:
    s = s.replace(old_regex, new_regex, 1)

old_parse = """            val min = match.groupValues[2].toIntOrNull() ?: return@forEach
            val max = match.groupValues[3].toIntOrNull() ?: return@forEach
            if (max > min) result[match.groupValues[1]] = AxisRange(min, max)"""
new_parse = """            val current = match.groupValues[2].toIntOrNull()
            val min = match.groupValues[3].toIntOrNull() ?: return@forEach
            val max = match.groupValues[4].toIntOrNull() ?: return@forEach
            if (max > min) result[match.groupValues[1]] = AxisRange(min, max, current)"""
if old_parse in s:
    s = s.replace(old_parse, new_parse, 1)
elif new_parse not in s:
    raise SystemExit("Axis range parser block not found")
reader.write_text(s)

capture = root / "input/CalibrationCapture.kt"
capture.write_text("""package com.gamepadbridge.app.input

import com.gamepadbridge.app.data.RawSource
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs

object CalibrationCapture {
    val events = MutableSharedFlow<RawSource>(extraBufferCapacity = 32)

    private val generation = AtomicInteger(0)
    private val axisRestValue = HashMap<String, Int>()

    fun start(node: String) {
        val token = generation.incrementAndGet()
        val axisRanges = DeviceNodeReader.getAxisRanges(node)
        synchronized(axisRestValue) {
            axisRestValue.clear()
            axisRanges.forEach { (code, range) ->
                range.currentValue?.let { axisRestValue[code] = it }
            }
        }
        thread(name = "calibration-capture") {
            DeviceNodeReader.streamEvents(
                node = node,
                onLine = { line ->
                    when (line.type) {
                        "EV_KEY" -> when (line.value.trim()) {
                            "DOWN" -> events.tryEmit(RawSource.Key(line.code))
                        }
                        "EV_ABS" -> {
                            val raw = parseRawValue(line.value) ?: return@streamEvents
                            val range = axisRanges[line.code]
                            val isHat = line.code.startsWith("ABS_HAT")
                            val isTrigger = line.code in setOf(
                                "ABS_Z", "ABS_RZ", "ABS_BRAKE", "ABS_GAS", "ABS_THROTTLE"
                            )
                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) {
                                    range?.currentValue
                                        ?: range?.let {
                                            if (isTrigger) it.min
                                            else ((it.min.toLong() + it.max.toLong()) / 2L).toInt()
                                        }
                                        ?: raw
                                }
                            }
                            val span = ((range?.max?.toLong() ?: 65535L) -
                                (range?.min?.toLong() ?: -32768L)).coerceAtLeast(1L)
                            val threshold = if (isHat) 0L else maxOf(6L, span / 128L)
                            if (abs(raw.toLong() - rest.toLong()) > threshold) {
                                events.tryEmit(RawSource.Axis(line.code, raw > rest))
                            }
                        }
                    }
                },
                shouldStop = { generation.get() != token }
            )
        }
    }

    fun stop(node: String? = null) {
        generation.incrementAndGet()
        if (node != null) DeviceNodeReader.stop(node)
    }

    private fun parseRawValue(value: String): Int? = runCatching {
        val cleaned = value.trim().removePrefix("0x")
        if (cleaned.isEmpty()) return null
        cleaned.toLong(16).toInt()
    }.getOrNull()
}
""")

service = root / "service/GamepadBridgeService.kt"
s = service.read_text()
old_loop = """                for ((xbox, positiveDirection) in mappings) {
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2"""
new_loop = """                for ((xbox, positiveDirection) in mappings) {
                    val dpadKey = ControllerProfile.outputKeyCode(xbox)
                    val isDpad = xbox == XboxInput.DPAD_UP || xbox == XboxInput.DPAD_DOWN ||
                        xbox == XboxInput.DPAD_LEFT || xbox == XboxInput.DPAD_RIGHT
                    if (isDpad && dpadKey != null) {
                        val center = range?.let { (it.min.toLong() + it.max.toLong()) / 2L } ?: 0L
                        val active = if (positiveDirection) raw.toLong() > center else raw.toLong() < center
                        val changed = synchronized(pressedKeyCodes) {
                            if (active) pressedKeyCodes.add(dpadKey) else pressedKeyCodes.remove(dpadKey)
                        }
                        if (changed) ShizukuBridge.injectKey(dpadKey, active, InputDevice.SOURCE_GAMEPAD)
                        continue
                    }
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2"""
if old_loop in s:
    s = s.replace(old_loop, new_loop, 1)
elif "val isDpad = xbox == XboxInput.DPAD_UP" not in s:
    raise SystemExit("Service axis mapping loop not found")
service.write_text(s)

print("Consolidated axis rest calibration, HAT capture, and digital D-pad output.")
