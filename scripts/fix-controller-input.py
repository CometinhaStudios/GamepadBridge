from pathlib import Path
import re

root = Path('app/src/main/java/com/gamepadbridge/app')

reader = root / 'input/DeviceNodeReader.kt'
s = reader.read_text()
old = '''        fun normalize(raw: Int): Float {
            if (max <= min) return 0f
            return (((raw.coerceIn(min, max) - min).toFloat() / (max - min)) * 2f - 1f).coerceIn(-1f, 1f)
        }
'''
new = '''        fun centerValue(): Int {
            if (max <= min) return 0
            val midpoint = ((min.toLong() + max.toLong()) / 2L).toInt()
            val current = currentValue ?: return midpoint
            val quarter = (max.toLong() - min.toLong()) / 4L
            return if (current.toLong() in (min.toLong() + quarter)..(max.toLong() - quarter)) current else midpoint
        }

        fun normalize(raw: Int, center: Int = centerValue()): Float {
            if (max <= min) return 0f
            val c = center.coerceIn(min, max)
            val value = raw.coerceIn(min, max)
            return if (value >= c) {
                val span = (max.toLong() - c.toLong()).coerceAtLeast(1L)
                ((value.toLong() - c.toLong()).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            } else {
                val span = (c.toLong() - min.toLong()).coerceAtLeast(1L)
                -((c.toLong() - value.toLong()).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            }
        }
'''
if old not in s:
    raise SystemExit('Could not find AxisRange.normalize block')
s = s.replace(old, new, 1)
reader.write_text(s)

cal = root / 'input/CalibrationCapture.kt'
cal.write_text('''package com.gamepadbridge.app.input

import com.gamepadbridge.app.data.RawSource
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs

object CalibrationCapture {
    val events = MutableSharedFlow<RawSource>(extraBufferCapacity = 64)
    private val generation = AtomicInteger(0)
    private val axisRestValue = HashMap<String, Int>()

    fun start(node: String) {
        val token = generation.incrementAndGet()
        val ranges = DeviceNodeReader.getAxisRanges(node)
        synchronized(axisRestValue) {
            axisRestValue.clear()
            ranges.forEach { (code, range) ->
                axisRestValue[code] = if (code.startsWith("ABS_HAT")) 0 else range.centerValue()
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
                            val isHat = line.code.startsWith("ABS_HAT")
                            val range = ranges[line.code]
                            val rest = synchronized(axisRestValue) {
                                axisRestValue.getOrPut(line.code) { if (isHat) 0 else range?.centerValue() ?: raw }
                            }
                            val span = if (isHat) 1L else ((range?.max?.toLong() ?: 65535L) - (range?.min?.toLong() ?: -32768L)).coerceAtLeast(1L)
                            val threshold = if (isHat) 0L else maxOf(4L, span / 100L)
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
        value.trim().removePrefix("0x").toLong(16).toInt()
    }.getOrNull()
}
''')

service = root / 'service/GamepadBridgeService.kt'
s = service.read_text()
old_dpad = '''                    if (isDpad && dpadKey != null) {
                        val center = range?.let { (it.min.toLong() + it.max.toLong()) / 2L } ?: 0L
                        val active = if (positiveDirection) raw.toLong() > center else raw.toLong() < center
'''
new_dpad = '''                    if (isDpad && dpadKey != null) {
                        val isHat = line.code.startsWith("ABS_HAT")
                        val center = if (isHat) 0L else range?.centerValue()?.toLong() ?: 0L
                        val active = if (positiveDirection) raw.toLong() > center else raw.toLong() < center
'''
if old_dpad not in s:
    raise SystemExit('Could not find D-pad axis handling block')
s = s.replace(old_dpad, new_dpad, 1)
old_norm = '''                        range != null && isTrigger -> range.normalizeTrigger(raw)
                        range != null -> range.normalize(raw)
'''
new_norm = '''                        range != null && isTrigger -> range.normalizeTrigger(raw)
                        range != null -> range.normalize(raw, range.centerValue())
'''
if old_norm not in s:
    raise SystemExit('Could not find axis normalization block')
s = s.replace(old_norm, new_norm, 1)
service.write_text(s)

print('Applied HAT sign handling and centered, range-aware analog normalization.')
