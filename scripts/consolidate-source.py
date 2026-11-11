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
s = capture.read_text()
old_start = """        synchronized(axisRestValue) { axisRestValue.clear() }
        thread(name = "calibration-capture") {"""
new_start = """        val axisRanges = DeviceNodeReader.getAxisRanges(node)
        synchronized(axisRestValue) {
            axisRestValue.clear()
            axisRanges.forEach { (code, range) ->
                range.currentValue?.let { axisRestValue[code] = it }
            }
        }
        thread(name = "calibration-capture") {"""
if old_start in s:
    s = s.replace(old_start, new_start, 1)
elif "val axisRanges = DeviceNodeReader.getAxisRanges(node)" not in s:
    raise SystemExit("Calibration start anchor not found")

old_capture = """                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) { raw }
                            }
                            if (abs(raw.toLong() - rest.toLong()) > 45L) {"""
new_capture = """                            val range = axisRanges[line.code]
                            val isHat = line.code.startsWith("ABS_HAT")
                            val isTrigger = line.code in setOf("ABS_Z", "ABS_RZ", "ABS_BRAKE", "ABS_GAS", "ABS_THROTTLE")
                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) {
                                    range?.currentValue
                                        ?: range?.let {
                                            if (isTrigger) it.min else ((it.min.toLong() + it.max.toLong()) / 2L).toInt()
                                        }
                                        ?: raw
                                }
                            }
                            val span = ((range?.max?.toLong() ?: 65535L) -
                                (range?.min?.toLong() ?: -32768L)).coerceAtLeast(1L)
                            val threshold = if (isHat) 0L else maxOf(6L, span / 128L)
                            if (abs(raw.toLong() - rest.toLong()) > threshold) {"""
if old_capture in s:
    s = s.replace(old_capture, new_capture, 1)
elif "val isHat = line.code.startsWith(\"ABS_HAT\")" not in s:
    raise SystemExit("Calibration axis capture block not found")
capture.write_text(s)

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
