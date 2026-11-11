from pathlib import Path

root = Path("app/src/main/java/com/gamepadbridge/app")
(root / "input").mkdir(parents=True, exist_ok=True)
(root / "input/AnalogAutoMapper.kt").write_text(r"""package com.gamepadbridge.app.input

import android.view.InputDevice
import android.view.MotionEvent
import com.gamepadbridge.app.data.RawSource
import com.gamepadbridge.app.data.XboxInput

object AnalogAutoMapper {
    data class Detection(val mapping: Map<XboxInput, RawSource>, val message: String)

    fun detect(deviceName: String, node: String): Detection {
        val candidates = InputDevice.getDeviceIds().mapNotNull { id ->
            runCatching { InputDevice.getDevice(id) }.getOrNull()
        }.filter { it.name.equals(deviceName, ignoreCase = true) }

        val inputDevice = candidates.maxByOrNull { device ->
            val axes = device.motionRanges.map { it.axis }.toSet()
            (if ((device.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) 100 else 0) +
                axes.count { it in setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
                    MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY) }
        } ?: return Detection(emptyMap(), "O Android não encontrou um dispositivo com o mesmo nome. O mapeamento manual continua disponível.")

        val androidAxes = inputDevice.motionRanges.map { it.axis }.toSet()
        val rawRanges = DeviceNodeReader.getAxisRanges(node)
        val result = linkedMapOf<XboxInput, RawSource>()

        fun pair(negative: XboxInput, positive: XboxInput, androidAxis: Int, rawCode: String) {
            if (androidAxis !in androidAxes || rawCode !in rawRanges) return
            result[negative] = RawSource.Axis(rawCode, false)
            result[positive] = RawSource.Axis(rawCode, true)
        }

        pair(XboxInput.LS_LEFT, XboxInput.LS_RIGHT, MotionEvent.AXIS_X, "ABS_X")
        pair(XboxInput.LS_UP, XboxInput.LS_DOWN, MotionEvent.AXIS_Y, "ABS_Y")

        val zPair = MotionEvent.AXIS_Z in androidAxes && MotionEvent.AXIS_RZ in androidAxes &&
            "ABS_Z" in rawRanges && "ABS_RZ" in rawRanges
        if (zPair) {
            pair(XboxInput.RS_LEFT, XboxInput.RS_RIGHT, MotionEvent.AXIS_Z, "ABS_Z")
            pair(XboxInput.RS_UP, XboxInput.RS_DOWN, MotionEvent.AXIS_RZ, "ABS_RZ")
        } else {
            pair(XboxInput.RS_LEFT, XboxInput.RS_RIGHT, MotionEvent.AXIS_RX, "ABS_RX")
            pair(XboxInput.RS_UP, XboxInput.RS_DOWN, MotionEvent.AXIS_RY, "ABS_RY")
        }

        val axesFound = result.size / 2
        val message = if (result.isEmpty()) {
            "O Android não expôs eixos compatíveis para este dispositivo. Faça o mapeamento manual."
        } else {
            "$axesFound eixo(s) reconhecido(s) automaticamente em ${inputDevice.name}. Confira no teste antes de usar em jogos."
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
