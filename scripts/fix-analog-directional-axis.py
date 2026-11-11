from pathlib import Path

p = Path("app/src/main/java/com/gamepadbridge/app/service/GamepadBridgeService.kt")
s = p.read_text()
start = s.index("                for ((xbox, positiveDirection) in mappings) {")
end_marker = "                val axes = mappedAxes.toIntArray()"
end = s.index(end_marker, start)
replacement = """                // Preserve the original per-mapping processing. Each calibrated
                // direction contributes its own polarity; do not collapse the two
                // directions into a single preferred calibration entry.
                for ((xbox, positiveDirection) in mappings) {
                    val dpadKey = ControllerProfile.outputKeyCode(xbox)
                    val isDpad = xbox == XboxInput.DPAD_UP || xbox == XboxInput.DPAD_DOWN ||
                        xbox == XboxInput.DPAD_LEFT || xbox == XboxInput.DPAD_RIGHT
                    if (isDpad && dpadKey != null) {
                        val isHat = line.code.startsWith("ABS_HAT")
                        val center = if (isHat) 0L else range?.centerValue()?.toLong() ?: 0L
                        val active = if (positiveDirection) raw.toLong() > center else raw.toLong() < center
                        val changed = synchronized(pressedKeyCodes) {
                            if (active) pressedKeyCodes.add(dpadKey) else pressedKeyCodes.remove(dpadKey)
                        }
                        if (changed) ShizukuBridge.injectKey(dpadKey, active, InputDevice.SOURCE_GAMEPAD)
                        continue
                    }
                    // For paired directions sharing an output axis, only the mapping whose
                    // calibrated raw direction matches the current movement may write.
                    // This prevents the inactive opposite direction from overwriting it.
                    if (xbox == XboxInput.RS_UP || xbox == XboxInput.RS_DOWN ||
                        xbox == XboxInput.LS_LEFT || xbox == XboxInput.LS_RIGHT) {
                        val center = range?.centerValue()?.toLong() ?: 0L
                        val rawIsPositive = raw.toLong() > center
                        if (raw.toLong() != center && positiveDirection != rawIsPositive) continue
                    }
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2
                    val normalized = when {
                        range != null && isTrigger -> range.normalizeTrigger(raw)
                        range != null -> range.normalize(raw, range.centerValue())
                        isTrigger -> raw.coerceIn(0, 255) / 255f
                        raw in 0..255 -> (raw / 127.5f - 1f).coerceIn(-1f, 1f)
                        else -> (raw / 32767f).coerceIn(-1f, 1f)
                    }

                    val positiveTarget = xbox in setOf(
                        XboxInput.LS_RIGHT, XboxInput.LS_DOWN,
                        XboxInput.RS_RIGHT, XboxInput.RS_DOWN
                    )
                    val directionalTarget = xbox in setOf(
                        XboxInput.LS_LEFT, XboxInput.LS_RIGHT, XboxInput.LS_UP, XboxInput.LS_DOWN,
                        XboxInput.RS_LEFT, XboxInput.RS_RIGHT, XboxInput.RS_UP, XboxInput.RS_DOWN
                    )
                    val directed = when {
                        isTrigger -> if (positiveDirection) normalized else 1f - normalized
                        directionalTarget -> if (positiveDirection == positiveTarget) normalized else -normalized
                        positiveDirection -> normalized
                        else -> -normalized
                    }.let { if (isTrigger) it.coerceIn(0f, 1f) else it.coerceIn(-1f, 1f) }

                    synchronized(axisValues) { axisValues[axis] = directed }
                }

"""
s = s[:start] + replacement + s[end:]
if "Preserve the original per-mapping processing" not in s:
    raise SystemExit("Rollback marker missing")
p.write_text(s)
print("Restored per-direction analog mapping.")
