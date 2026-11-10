from pathlib import Path

root = Path("app/src/main/java/com/gamepadbridge/app")

# Preserve the live ABS value reported by getevent -lp. This gives calibration
# a real neutral point before the first movement event arrives.
p = root / "input/DeviceNodeReader.kt"
s = p.read_text()
s = s.replace(
    "data class AxisRange(val min: Int, val max: Int) {",
    "data class AxisRange(val min: Int, val max: Int, val currentValue: Int? = null) {",
    1
)
old = r'''val regex = Regex("""(ABS_[A-Z0-9_]+)\s*:\s*value\s+-?\d+,\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")'''
new = r'''val regex = Regex("""(ABS_[A-Z0-9_]+)\s*:\s*value\s+(-?\d+),\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")'''
if old not in s: raise SystemExit("DeviceNodeReader axis range regex not found")
s = s.replace(old, new, 1)
old = '''            val min = match.groupValues[2].toIntOrNull() ?: return@forEach
            val max = match.groupValues[3].toIntOrNull() ?: return@forEach
            if (max > min) result[match.groupValues[1]] = AxisRange(min, max)'''
new = '''            val current = match.groupValues[2].toIntOrNull()
            val min = match.groupValues[3].toIntOrNull() ?: return@forEach
            val max = match.groupValues[4].toIntOrNull() ?: return@forEach
            if (max > min) result[match.groupValues[1]] = AxisRange(min, max, current)'''
if old not in s: raise SystemExit("DeviceNodeReader axis range parser not found")
s = s.replace(old, new, 1)
p.write_text(s)

# Seed each axis at its actual resting value and use a small, range-aware
# threshold. Hat switches have discrete -1/0/1 values.
p = root / "input/CalibrationCapture.kt"
s = p.read_text()
old = '''        synchronized(axisRestValue) { axisRestValue.clear() }
        thread(name = "calibration-capture") {'''
new = '''        val axisRanges = DeviceNodeReader.getAxisRanges(node)
        synchronized(axisRestValue) {
            axisRestValue.clear()
            axisRanges.forEach { (code, range) ->
                range.currentValue?.let { axisRestValue[code] = it }
            }
        }
        thread(name = "calibration-capture") {'''
if old not in s: raise SystemExit("CalibrationCapture start anchor not found")
s = s.replace(old, new, 1)
old = '''                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) { raw }
                            }
                            if (abs(raw.toLong() - rest.toLong()) > 45L) {'''
new = '''                            val range = axisRanges[line.code]
                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) {
                                    range?.currentValue
                                        ?: range?.let { (it.min + it.max) / 2 }
                                        ?: raw
                                }
                            }
                            val isHat = line.code.startsWith("ABS_HAT")
                            val threshold = if (isHat) 0L else maxOf(
                                6L,
                                ((range?.max?.toLong() ?: 32767L) -
                                    (range?.min?.toLong() ?: -32768L)) / 64L
                            )
                            if (abs(raw.toLong() - rest.toLong()) > threshold) {'''
if old not in s: raise SystemExit("CalibrationCapture rest/threshold block not found")
s = s.replace(old, new, 1)
p.write_text(s)

# Convert ABS_HAT0X/Y mappings into digital D-pad key transitions.
p = root / "service/GamepadBridgeService.kt"
s = p.read_text()
old = '''                for ((xbox, positiveDirection) in mappings) {
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2'''
new = '''                for ((xbox, positiveDirection) in mappings) {
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
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2'''
if old not in s: raise SystemExit("GamepadBridgeService ABS mapping loop not found")
s = s.replace(old, new, 1)
p.write_text(s)

print("Applied neutral-axis calibration and digital D-pad hat handling.")
