from pathlib import Path

p = Path("app/src/main/java/com/gamepadbridge/app/service/GamepadBridgeService.kt")
s = p.read_text()
start = s.index("                for ((xbox, positiveDirection) in mappings) {")
end_marker = """                    synchronized(axisValues) { axisValues[axis] = directed }
                }

                val axes = mappedAxes.toIntArray()"""
end = s.index(end_marker, start) + len("""                    synchronized(axisValues) { axisValues[axis] = directed }
                }""")
replacement = '''                // D-pad HAT values are digital; keep their key state independent
                // from analog stick axes.
                for ((xbox, positiveDirection) in mappings) {
                    val dpadKey = ControllerProfile.outputKeyCode(xbox)
                    val isDpad = xbox == XboxInput.DPAD_UP || xbox == XboxInput.DPAD_DOWN ||
                        xbox == XboxInput.DPAD_LEFT || xbox == XboxInput.DPAD_RIGHT
                    if (!isDpad || dpadKey == null) continue
                    val center = if (line.code.startsWith("ABS_HAT")) 0L
                        else range?.centerValue()?.toLong() ?: 0L
                    val active = if (positiveDirection) raw.toLong() > center else raw.toLong() < center
                    val changed = synchronized(pressedKeyCodes) {
                        if (active) pressedKeyCodes.add(dpadKey) else pressedKeyCodes.remove(dpadKey)
                    }
                    if (changed) ShizukuBridge.injectKey(dpadKey, active, InputDevice.SOURCE_GAMEPAD)
                }

                // Resolve each physical axis once. Previously the two directional
                // mappings could write the same output axis repeatedly, making the
                // result depend on map iteration order and stale direction metadata.
                val stickMappings = mappings.filter { (xbox, _) ->
                    xbox !in setOf(XboxInput.L2, XboxInput.R2) &&
                        ControllerProfile.outputAxis(xbox) != null &&
                        xbox !in setOf(
                            XboxInput.DPAD_UP, XboxInput.DPAD_DOWN,
                            XboxInput.DPAD_LEFT, XboxInput.DPAD_RIGHT
                        )
                }
                stickMappings.groupBy { ControllerProfile.outputAxis(it.first)!! }
                    .forEach { (axis, axisEntries) ->
                        val positiveTarget = when (axis) {
                            android.view.MotionEvent.AXIS_X -> XboxInput.LS_RIGHT
                            android.view.MotionEvent.AXIS_Y -> XboxInput.LS_DOWN
                            android.view.MotionEvent.AXIS_Z -> XboxInput.RS_RIGHT
                            android.view.MotionEvent.AXIS_RZ -> XboxInput.RS_DOWN
                            else -> null
                        }
                        val negativeTarget = when (axis) {
                            android.view.MotionEvent.AXIS_X -> XboxInput.LS_LEFT
                            android.view.MotionEvent.AXIS_Y -> XboxInput.LS_UP
                            android.view.MotionEvent.AXIS_Z -> XboxInput.RS_LEFT
                            android.view.MotionEvent.AXIS_RZ -> XboxInput.RS_UP
                            else -> null
                        }
                        // true means increasing raw values represent the positive
                        // output direction (right/down). Derive this from the
                        // positive calibration, or invert the negative calibration.
                        val rawPositiveIsOutputPositive =
                            axisEntries.firstOrNull { it.first == positiveTarget }?.second
                                ?: axisEntries.firstOrNull { it.first == negativeTarget }?.second?.not()
                                ?: axisEntries.first().second
                        val normalized = when {
                            range != null -> range.normalize(raw, range.centerValue())
                            raw in 0..255 -> (raw / 127.5f - 1f).coerceIn(-1f, 1f)
                            else -> (raw / 32767f).coerceIn(-1f, 1f)
                        }
                        val outputValue = if (rawPositiveIsOutputPositive) normalized else -normalized
                        synchronized(axisValues) { axisValues[axis] = outputValue.coerceIn(-1f, 1f) }
                    }

                // Triggers are unipolar and must not share stick-axis polarity logic.
                mappings.filter { it.first == XboxInput.L2 || it.first == XboxInput.R2 }
                    .forEach { (xbox, positiveDirection) ->
                        val axis = ControllerProfile.outputAxis(xbox) ?: return@forEach
                        val normalized = when {
                            range != null -> range.normalizeTrigger(raw)
                            raw in 0..255 -> raw / 255f
                            else -> ((raw + 32768f) / 65535f).coerceIn(0f, 1f)
                        }
                        val value = if (positiveDirection) normalized else 1f - normalized
                        synchronized(axisValues) { axisValues[axis] = value.coerceIn(0f, 1f) }
                    }'''
s = s[:start] + replacement + s[end:]
if "Resolve each physical axis once" not in s:
    raise SystemExit("Analog refactor insertion failed")
p.write_text(s)
print("Refactored analog mapping to resolve each output axis once.")
