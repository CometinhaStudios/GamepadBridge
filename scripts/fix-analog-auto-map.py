from pathlib import Path

root = Path("app/src/main/java/com/gamepadbridge/app")
(root / "input").mkdir(parents=True, exist_ok=True)
(root / "input/AnalogAutoMapper.kt").write_text(r"""package com.gamepadbridge.app.input

import com.gamepadbridge.app.data.RawSource
import com.gamepadbridge.app.data.XboxInput

object AnalogAutoMapper {
    data class Detection(val mapping: Map<XboxInput, RawSource>, val message: String)

    // Read capabilities from the exact evdev node already selected for button mapping.
    // Android InputDevice names/sources are deliberately not consulted: Android can
    // expose the buttons and raw evdev axes under different device identities.
    fun detect(deviceName: String, node: String): Detection {
        val ranges = DeviceNodeReader.getAxisRanges(node)
        val result = linkedMapOf<XboxInput, RawSource>()

        fun hasCenteredRange(code: String, allowUnsigned: Boolean = false): Boolean {
            val range = ranges[code] ?: return false
            if (range.max <= range.min) return false
            // Z/RZ are often one-way triggers (0..255), not right-stick axes.
            // RX/RY are conventional right-stick axes even on devices using
            // unsigned 0..255 ranges, so those are allowed explicitly.
            return allowUnsigned || (range.min < 0 && range.max > 0)
        }

        fun pair(negative: XboxInput, positive: XboxInput, code: String, allowUnsigned: Boolean = false) {
            if (!hasCenteredRange(code, allowUnsigned)) return
            result[negative] = RawSource.Axis(code, false)
            result[positive] = RawSource.Axis(code, true)
        }

        // The left stick is conventionally ABS_X / ABS_Y.
        pair(XboxInput.LS_LEFT, XboxInput.LS_RIGHT, "ABS_X", allowUnsigned = true)
        pair(XboxInput.LS_UP, XboxInput.LS_DOWN, "ABS_Y", allowUnsigned = true)

        // Prefer RX/RY for the right stick. Fall back to Z/RZ only when they
        // have a bipolar range, avoiding accidental mapping of analog triggers.
        val hasRxRy = hasCenteredRange("ABS_RX", allowUnsigned = true) &&
            hasCenteredRange("ABS_RY", allowUnsigned = true)
        if (hasRxRy) {
            pair(XboxInput.RS_LEFT, XboxInput.RS_RIGHT, "ABS_RX", allowUnsigned = true)
            pair(XboxInput.RS_UP, XboxInput.RS_DOWN, "ABS_RY", allowUnsigned = true)
        } else {
            pair(XboxInput.RS_LEFT, XboxInput.RS_RIGHT, "ABS_Z")
            pair(XboxInput.RS_UP, XboxInput.RS_DOWN, "ABS_RZ")
        }

        val axesFound = result.size / 2
        val message = if (result.isEmpty()) {
            "Nenhum eixo analógico compatível foi encontrado no dispositivo selecionado ($deviceName). Confira se ele aparece como ABS_X/ABS_Y, ABS_RX/ABS_RY ou ABS_Z/ABS_RZ."
        } else {
            "$axesFound eixo(s) analógico(s) encontrado(s) diretamente no dispositivo selecionado ($deviceName), sem depender do nome reconhecido pelo Android."
        }
        return Detection(result, message)
    }
}
""")

screen = root / "ui/MappingScreen.kt"
s = screen.read_text()
if "import com.gamepadbridge.app.input.AnalogAutoMapper" not in s:
    s = s.replace("import com.gamepadbridge.app.input.CalibrationCapture",
        "import com.gamepadbridge.app.input.AnalogAutoMapper\nimport com.gamepadbridge.app.input.CalibrationCapture")

old = """    val scope = rememberCoroutineScope()

    DisposableEffect(device.node) {"""
new = """    val scope = rememberCoroutineScope()
    var autoMapping by remember { mutableStateOf(false) }
    var autoMapMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(device.node) {"""
if old not in s: raise SystemExit("state anchor missing")
s = s.replace(old, new, 1)

old = """        if (candidate != null) return@LaunchedEffect
        candidate = CalibrationCapture.events.first { source ->
            collected.values.none { it.identity() == source.identity() }
        }"""
new = """        if (candidate != null) return@LaunchedEffect
        if (collected.containsKey(target)) {
            index = (index + 1).coerceAtMost(order.size)
            return@LaunchedEffect
        }
        candidate = CalibrationCapture.events.first { source ->
            collected.values.none { it.identity() == source.identity() }
        }"""
if old not in s: raise SystemExit("capture anchor missing")
s = s.replace(old, new, 1)

old = """        Text(device.name, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        if (index < order.size) {"""
new = """        Text(device.name, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            scope.launch {
                autoMapping = true
                candidate = null
                val detection = withContext(Dispatchers.IO) {
                    AnalogAutoMapper.detect(device.name, device.node)
                }
                if (detection.mapping.isNotEmpty()) {
                    collected.putAll(detection.mapping)
                    val next = order.indexOfFirst { it !in collected.keys }
                    index = if (next < 0) order.size else next
                }
                autoMapMessage = detection.message
                autoMapping = false
            }
        }, enabled = !autoMapping) {
            Text(if (autoMapping) "Detectando analógicos..." else "Detectar analógicos automaticamente")
        }
        autoMapMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))

        if (index < order.size) {"""
if old not in s: raise SystemExit("header anchor missing")
s = s.replace(old, new, 1)

old = """                        collected[current] = detected
                        candidate = null
                        index = (index + 1).coerceAtMost(order.size)"""
new = """                        collected[current] = detected
                        candidate = null
                        val next = (index + 1 until order.size).firstOrNull { order[it] !in collected.keys }
                        index = next ?: order.size"""
if old not in s: raise SystemExit("confirm anchor missing")
s = s.replace(old, new, 1)
screen.write_text(s)
print("Android InputDevice analog auto-mapping added.")
