from pathlib import Path

profile = Path("app/src/main/java/com/gamepadbridge/app/data/ControllerProfile.kt")
s = profile.read_text()
old = '''    LS_X("Analógico Esquerdo — Eixo X", isAxis = true),
    LS_Y("Analógico Esquerdo — Eixo Y", isAxis = true),
    RS_X("Analógico Direito — Eixo X", isAxis = true),
    RS_Y("Analógico Direito — Eixo Y", isAxis = true);'''
new = '''    LS_X("Analógico Esquerdo — Eixo X (legado)", isAxis = true),
    LS_Y("Analógico Esquerdo — Eixo Y (legado)", isAxis = true),
    RS_X("Analógico Direito — Eixo X (legado)", isAxis = true),
    RS_Y("Analógico Direito — Eixo Y (legado)", isAxis = true),
    LS_LEFT("Analógico esquerdo — mover para ESQUERDA", isAxis = true),
    LS_RIGHT("Analógico esquerdo — mover para DIREITA", isAxis = true),
    LS_UP("Analógico esquerdo — mover para CIMA", isAxis = true),
    LS_DOWN("Analógico esquerdo — mover para BAIXO", isAxis = true),
    RS_LEFT("Analógico direito — mover para ESQUERDA", isAxis = true),
    RS_RIGHT("Analógico direito — mover para DIREITA", isAxis = true),
    RS_UP("Analógico direito — mover para CIMA", isAxis = true),
    RS_DOWN("Analógico direito — mover para BAIXO", isAxis = true);'''
if old not in s: raise SystemExit("enum axes block not found")
s=s.replace(old,new)
old='''            L2, R2, LS_X, LS_Y, RS_X, RS_Y
        )'''
new='''            L2, R2,
            LS_LEFT, LS_RIGHT, LS_UP, LS_DOWN,
            RS_LEFT, RS_RIGHT, RS_UP, RS_DOWN
        )'''
if old not in s: raise SystemExit("mapping order block not found")
s=s.replace(old,new)
old='''            XboxInput.LS_X -> MotionEvent.AXIS_X
            XboxInput.LS_Y -> MotionEvent.AXIS_Y
            XboxInput.RS_X -> MotionEvent.AXIS_Z
            XboxInput.RS_Y -> MotionEvent.AXIS_RZ'''
new='''            XboxInput.LS_X, XboxInput.LS_LEFT, XboxInput.LS_RIGHT -> MotionEvent.AXIS_X
            XboxInput.LS_Y, XboxInput.LS_UP, XboxInput.LS_DOWN -> MotionEvent.AXIS_Y
            XboxInput.RS_X, XboxInput.RS_LEFT, XboxInput.RS_RIGHT -> MotionEvent.AXIS_Z
            XboxInput.RS_Y, XboxInput.RS_UP, XboxInput.RS_DOWN -> MotionEvent.AXIS_RZ'''
if old not in s: raise SystemExit("output axis block not found")
s=s.replace(old,new)
profile.write_text(s)

service = Path("app/src/main/java/com/gamepadbridge/app/service/GamepadBridgeService.kt")
s = service.read_text()
s=s.replace('''        val axisMap = HashMap<String, Pair<XboxInput, Boolean>>()''','''        val axisMap = HashMap<String, MutableList<Pair<XboxInput, Boolean>>>()''')
s=s.replace('''                is RawSource.Axis -> axisMap[input.code] = output to input.positiveDirection''','''                is RawSource.Axis -> axisMap.getOrPut(input.code) { mutableListOf() }.add(output to input.positiveDirection)''')
s=s.replace('''        val mappedAxes = axisMap.values.mapNotNull { ControllerProfile.outputAxis(it.first) }.distinct()''','''        val mappedAxes = axisMap.values.flatten().mapNotNull { ControllerProfile.outputAxis(it.first) }.distinct()''')
s=s.replace('''        axisMap: Map<String, Pair<XboxInput, Boolean>>,''','''        axisMap: Map<String, List<Pair<XboxInput, Boolean>>>,''')
old='''                val (xbox, positiveDirection) = axisMap[line.code] ?: return
                val axis = ControllerProfile.outputAxis(xbox) ?: return
                val raw = parseRawValue(line.value) ?: return
                val range = axisRanges[line.code]
                val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2
                val normalized = when {
                    range != null && isTrigger -> range.normalizeTrigger(raw)
                    range != null -> range.normalize(raw)
                    isTrigger -> ((raw.coerceIn(0, 255)) / 255f)
                    raw in 0..255 -> (raw / 127.5f - 1f).coerceIn(-1f, 1f)
                    else -> (raw / 32767f).coerceIn(-1f, 1f)
                }
                val directed = when {
                    positiveDirection -> normalized
                    isTrigger -> 1f - normalized
                    else -> -normalized
                }.let { if (isTrigger) it.coerceIn(0f, 1f) else it.coerceIn(-1f, 1f) }

                val axes: IntArray
                val values: FloatArray
                synchronized(axisValues) {
                    axisValues[axis] = directed
                    axes = mappedAxes.toIntArray()
                    values = FloatArray(axes.size) { index -> axisValues[axes[index]] ?: 0f }
                }
                ShizukuBridge.injectAxisBatch(axes, values, InputDevice.SOURCE_JOYSTICK)'''
new='''                val mappings = axisMap[line.code] ?: return
                val raw = parseRawValue(line.value) ?: return
                val range = axisRanges[line.code]
                for ((xbox, positiveDirection) in mappings) {
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2
                    val normalized = when {
                        range != null && isTrigger -> range.normalizeTrigger(raw)
                        range != null -> range.normalize(raw)
                        isTrigger -> (raw.coerceIn(0, 255) / 255f)
                        raw in 0..255 -> (raw / 127.5f - 1f).coerceIn(-1f, 1f)
                        else -> (raw / 32767f).coerceIn(-1f, 1f)
                    }
                    val positiveTarget = xbox in setOf(
                        XboxInput.LS_RIGHT, XboxInput.LS_DOWN,
                        XboxInput.RS_RIGHT, XboxInput.RS_DOWN
                    )
                    val directed = when {
                        isTrigger -> if (positiveDirection) normalized else 1f - normalized
                        xbox in setOf(
                            XboxInput.LS_LEFT, XboxInput.LS_RIGHT, XboxInput.LS_UP, XboxInput.LS_DOWN,
                            XboxInput.RS_LEFT, XboxInput.RS_RIGHT, XboxInput.RS_UP, XboxInput.RS_DOWN
                        ) -> if (positiveDirection == positiveTarget) normalized else -normalized
                        else -> if (positiveDirection) normalized else -normalized
                    }.let { if (isTrigger) it.coerceIn(0f, 1f) else it.coerceIn(-1f, 1f) }
                    synchronized(axisValues) { axisValues[axis] = directed }
                }
                val axes = mappedAxes.toIntArray()
                val values = synchronized(axisValues) {
                    FloatArray(axes.size) { index -> axisValues[axes[index]] ?: 0f }
                }
                ShizukuBridge.injectAxisBatch(axes, values, InputDevice.SOURCE_JOYSTICK)'''
if old not in s: raise SystemExit("EV_ABS handling block not found")
s=s.replace(old,new)
service.write_text(s)

visual = Path("app/src/main/java/com/gamepadbridge/app/ui/XboxControllerVisual.kt")
s = visual.read_text()
s=s.replace('''focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.L3), 320f, 325f, 62f)''','''focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.LS_LEFT, XboxInput.LS_RIGHT, XboxInput.LS_UP, XboxInput.LS_DOWN, XboxInput.L3), 320f, 325f, 62f)''')
s=s.replace('''focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.R3), 690f, 382f, 62f)''','''focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.RS_LEFT, XboxInput.RS_RIGHT, XboxInput.RS_UP, XboxInput.RS_DOWN, XboxInput.R3), 690f, 382f, 62f)''')
visual.write_text(s)
print("Applied directional analog mapping and multi-direction axis handling.")
