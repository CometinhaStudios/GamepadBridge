from pathlib import Path

ROOT = Path('app/src/main/java/com/gamepadbridge/app')

def replace_once(path: Path, source: str, old: str, new: str, label: str) -> str:
    if old not in source:
        raise SystemExit(f'{path}: expected block not found: {label}')
    return source.replace(old, new, 1)

p = ROOT / 'input/DeviceNodeReader.kt'
s = p.read_text()
s = replace_once(p, s,
'''        val supportsKeyboard: Boolean,
        val supportsGamepad: Boolean
    )''',
'''        val supportsKeyboard: Boolean,
        val supportsGamepad: Boolean,
        val nodes: List<String> = listOf(node)
    )''', 'DeviceEntry nodes')
s = replace_once(p, s,
'''import java.util.concurrent.TimeUnit
''',
'''import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
''', 'thread import')
s = replace_once(p, s,
'''    private val activeProcesses = ConcurrentHashMap<String, Process>()
''',
'''    private val activeProcesses = ConcurrentHashMap<String, Process>()

    fun encodeNodes(nodes: List<String>): String = nodes.distinct().joinToString("|")

    fun decodeNodes(encoded: String): List<String> {
        val nodes = encoded.split('|').map { it.trim() }
            .filter { it.matches(Regex("/dev/input/event\\\\d+")) }
        return nodes.ifEmpty { listOf(encoded) }
    }
''', 'node profile encoding')
s = replace_once(p, s,
'''        return entries.distinctBy { it.node }
    }

    /** Parse the kernel-reported min/max values so 8-bit and 16-bit axes both work. */
    fun getAxisRanges(node: String): Map<String, AxisRange> {''',
'''        val grouped = linkedMapOf<String, MutableList<DeviceEntry>>()
        entries.distinctBy { it.node }.forEach { entry ->
            val key = physicalGroupKey(entry.node)
            grouped.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return grouped.values.map { group ->
            val primary = group.firstOrNull { it.supportsGamepad } ?: group.first()
            val nodes = group.map { it.node }.distinct()
            primary.copy(
                kind = if (nodes.size > 1) "${primary.kind} • ${nodes.size} interfaces" else primary.kind,
                supportsKeyboard = group.any { it.supportsKeyboard },
                supportsGamepad = group.any { it.supportsGamepad },
                nodes = nodes
            )
        }
    }

    /** Group event nodes by their common physical sysfs parent, not by name alone. */
    private fun physicalGroupKey(node: String): String {
        val eventName = node.substringAfterLast('/')
        if (!eventName.matches(Regex("event\\\\d+"))) return "node:$node"
        val sysfsDevice = "/sys/class/input/$eventName/device"
        val resolved = runShell(arrayOf("readlink", "-f", sysfsDevice))?.trim().orEmpty()
        if (resolved.isNotBlank()) {
            val parent = resolved.replace(Regex("/input/input\\\\d+$"), "")
            if (parent.isNotBlank()) return "physical:$parent"
        }
        val phys = runShell(arrayOf("cat", "$sysfsDevice/phys"))?.trim().orEmpty()
        return if (phys.isNotBlank()) "physical:phys:$phys" else "node:$node"
    }

    /** Parse the kernel-reported min/max values so 8-bit and 16-bit axes both work. */
    fun getAxisRanges(node: String): Map<String, AxisRange> = getAxisRanges(listOf(node))

    fun getAxisRanges(nodes: List<String>): Map<String, AxisRange> {
        val result = linkedMapOf<String, AxisRange>()
        nodes.distinct().forEach { node -> result.putAll(getAxisRangesForNode(node)) }
        return result
    }

    private fun getAxisRangesForNode(node: String): Map<String, AxisRange> {''', 'group nodes and aggregate ranges')
s = replace_once(p, s,
'''    fun stop(node: String) {
        activeProcesses[node]?.let { process ->
            runCatching { process.destroy() }
        }
    }
''',
'''    /** Read all event nodes belonging to the selected physical controller concurrently. */
    fun streamEvents(nodes: List<String>, onLine: (RawLine) -> Unit, shouldStop: () -> Boolean) {
        val distinct = nodes.distinct()
        if (distinct.isEmpty()) return
        val workers = distinct.map { node ->
            thread(name = "gamepad-node-${node.substringAfterLast('/')}") {
                streamEvents(node, onLine, shouldStop)
            }
        }
        try {
            while (!shouldStop() && workers.any { it.isAlive }) Thread.sleep(25)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            distinct.forEach { stop(it) }
            workers.forEach { worker -> runCatching { worker.join(200) } }
        }
    }

    fun stop(node: String) {
        activeProcesses[node]?.let { process ->
            runCatching { process.destroy() }
        }
    }

    fun stop(nodes: List<String>) = nodes.distinct().forEach { stop(it) }
''', 'concurrent node readers')
p.write_text(s)

p = ROOT / 'input/CalibrationCapture.kt'
s = p.read_text()
s = replace_once(p, s,
'''    fun start(node: String) {
        val token = generation.incrementAndGet()
        val ranges = DeviceNodeReader.getAxisRanges(node)''',
'''    fun start(node: String) = start(listOf(node))

    fun start(nodes: List<String>) {
        val activeNodes = nodes.distinct()
        if (activeNodes.isEmpty()) return
        val token = generation.incrementAndGet()
        val ranges = DeviceNodeReader.getAxisRanges(activeNodes)''', 'capture start overload')
s = replace_once(p, s,
'''                            val threshold = if (isHat) 0L else maxOf(4L, span / 100L)''',
'''                            // v1.14 used 45 raw units. Keep that fallback when the
                            // kernel does not provide a parseable range; assuming a
                            // 16-bit span here makes 8-bit generic sticks invisible.
                            val threshold = when {
                                isHat -> 0L
                                range == null -> 45L
                                else -> maxOf(4L, span / 100L)
                            }''', '8-bit fallback threshold')
s = replace_once(p, s,
'''            DeviceNodeReader.streamEvents(
                node = node,''',
'''            DeviceNodeReader.streamEvents(
                nodes = activeNodes,''', 'capture all event nodes')
s = replace_once(p, s,
'''    fun stop(node: String? = null) {
        generation.incrementAndGet()
        if (node != null) DeviceNodeReader.stop(node)
    }''',
'''    fun stop(node: String? = null) {
        generation.incrementAndGet()
        if (node != null) DeviceNodeReader.stop(node)
    }

    fun stop(nodes: List<String>) {
        generation.incrementAndGet()
        DeviceNodeReader.stop(nodes)
    }''', 'stop all capture nodes')
p.write_text(s)

p = ROOT / 'input/AnalogAutoMapper.kt'
s = p.read_text()
s = replace_once(p, s,
'''    fun detect(deviceName: String, node: String): Detection {
        val ranges = DeviceNodeReader.getAxisRanges(node)''',
'''    fun detect(deviceName: String, node: String): Detection = detect(deviceName, listOf(node))

    fun detect(deviceName: String, nodes: List<String>): Detection {
        val ranges = DeviceNodeReader.getAxisRanges(nodes)''', 'auto-map all nodes')
p.write_text(s)

p = ROOT / 'ui/MappingScreen.kt'
s = p.read_text()
s = replace_once(p, s,
'''supportingContent = { Text("${device.kind} • ${device.node}") },''',
'''supportingContent = { Text("${device.kind} • ${device.nodes.joinToString()}") },''', 'show grouped event nodes')
s = replace_once(p, s,
'''    DisposableEffect(device.node) {
        CalibrationCapture.start(device.node)
        onDispose { CalibrationCapture.stop(device.node) }
    }''',
'''    DisposableEffect(device.nodes) {
        CalibrationCapture.start(device.nodes)
        onDispose { CalibrationCapture.stop(device.nodes) }
    }''', 'capture grouped nodes')
s = replace_once(p, s,
'''AnalogAutoMapper.detect(device.name, device.node)''',
'''AnalogAutoMapper.detect(device.name, device.nodes)''', 'auto-map grouped nodes')
s = replace_once(p, s,
'''ControllerProfile(device.node, collected.toMap(), device.name)''',
'''ControllerProfile(DeviceNodeReader.encodeNodes(device.nodes), collected.toMap(), device.name)''', 'persist grouped nodes')
p.write_text(s)

p = ROOT / 'service/GamepadBridgeService.kt'
s = p.read_text()
s = replace_once(p, s,
'''    @Volatile private var activeNode: String? = null''',
'''    @Volatile private var activeNodes: List<String> = emptyList()''', 'active nodes state')
s = replace_once(p, s,
'''        activeNode?.let(DeviceNodeReader::stop)''',
'''        DeviceNodeReader.stop(activeNodes)''', 'stop all active nodes')
s = replace_once(p, s,
'''        val axisRanges = DeviceNodeReader.getAxisRanges(profile.deviceName)''',
'''        val deviceNodes = DeviceNodeReader.decodeNodes(profile.deviceName)
        val axisRanges = DeviceNodeReader.getAxisRanges(deviceNodes)''', 'load ranges for all profile nodes')
s = replace_once(p, s,
'''        activeNode = profile.deviceName
        thread(name = "gamepad-bridge-reader") {
            DeviceNodeReader.streamEvents(
                node = profile.deviceName,''',
'''        activeNodes = deviceNodes
        thread(name = "gamepad-bridge-reader") {
            DeviceNodeReader.streamEvents(
                nodes = deviceNodes,''', 'listen to all saved nodes')
s = replace_once(p, s,
'''if (changed) ShizukuBridge.injectKey(dpadKey, active, InputDevice.SOURCE_GAMEPAD)''',
'''if (changed) UInputBridge.key(dpadKey, active)''', 'route dpad into virtual Xbox device')
p.write_text(s)

checks = {
    ROOT / 'input/DeviceNodeReader.kt': ['fun encodeNodes(nodes: List<String>)', 'private fun physicalGroupKey(node: String)', 'fun streamEvents(nodes: List<String>'],
    ROOT / 'input/CalibrationCapture.kt': ['range == null -> 45L', 'nodes = activeNodes'],
    ROOT / 'input/AnalogAutoMapper.kt': ['fun detect(deviceName: String, nodes: List<String>)'],
    ROOT / 'ui/MappingScreen.kt': ['CalibrationCapture.start(device.nodes)', 'DeviceNodeReader.encodeNodes(device.nodes)'],
    ROOT / 'service/GamepadBridgeService.kt': ['DeviceNodeReader.decodeNodes(profile.deviceName)', 'nodes = deviceNodes', 'UInputBridge.key(dpadKey, active)'],
}
for path, markers in checks.items():
    content = path.read_text()
    missing = [marker for marker in markers if marker not in content]
    if missing:
        raise SystemExit(f'{path}: missing expected changes: {missing}')

print('Verified multi-node capture, profile persistence, D-pad uinput routing, and 8-bit fallback threshold.')
