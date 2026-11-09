package com.gamepadbridge.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.InputDevice
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gamepadbridge.app.data.ControllerProfile
import com.gamepadbridge.app.data.ProfileStore
import com.gamepadbridge.app.data.RawSource
import com.gamepadbridge.app.data.XboxInput
import com.gamepadbridge.app.inject.ShizukuBridge
import com.gamepadbridge.app.input.DeviceNodeReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class GamepadBridgeService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var running = false
    private val hatState = ConcurrentHashMap<XboxInput, Boolean>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { runBridge() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runBridge() {
        val profile = ProfileStore(this).profileFlow.first()
        if (profile == null) {
            stopSelf()
            return
        }
        val keyMap = HashMap<String, XboxInput>()
        val axisMap = HashMap<String, MutableList<Pair<XboxInput, Boolean>>>()
        for ((xbox, source) in profile.mapping) {
            when (source) {
                is RawSource.Key -> keyMap[source.code] = xbox
                is RawSource.Axis -> axisMap.getOrPut(source.code) { mutableListOf() }.add(xbox to source.positiveDirection)
            }
        }
        running = true
        thread(name = "gamepad-bridge-reader") {
            DeviceNodeReader.streamEvents(
                node = profile.deviceName,
                onLine = { line -> handleLine(line, keyMap, axisMap) },
                shouldStop = { !running }
            )
        }
    }

    private fun handleLine(
        line: DeviceNodeReader.RawLine,
        keyMap: Map<String, XboxInput>,
        axisMap: Map<String, List<Pair<XboxInput, Boolean>>>
    ) {
        val source = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        when (line.type) {
            "EV_KEY" -> {
                val xbox = keyMap[line.code] ?: return
                val keyCode = ControllerProfile.outputKeyCode(xbox) ?: return
                val down = line.value.trim().equals("DOWN", ignoreCase = true) || line.value.trim() == "1"
                ShizukuBridge.injectKey(keyCode, down, source)
            }
            "EV_ABS" -> {
                val raw = parseRaw(line.value) ?: return
                val mappings = axisMap[line.code] ?: return
                for ((xbox, positiveDirection) in mappings) {
                    if (isDpad(xbox)) {
                        val active = if (positiveDirection) raw > 0 else raw < 0
                        val wasActive = hatState.put(xbox, active) ?: false
                        if (active != wasActive) {
                            ControllerProfile.outputKeyCode(xbox)?.let { ShizukuBridge.injectKey(it, active, source) }
                        }
                    } else {
                        val axis = ControllerProfile.outputAxis(xbox) ?: continue
                        val normalized = when (xbox) {
                            XboxInput.L2, XboxInput.R2 -> normalizeTrigger(raw)
                            else -> (normalizeStick(raw) * if (positiveDirection) 1f else -1f).coerceIn(-1f, 1f)
                        }
                        ShizukuBridge.injectAxisBatch(intArrayOf(axis), floatArrayOf(normalized), source)
                    }
                }
            }
        }
    }

    private fun parseRaw(value: String): Int? {
        val cleaned = value.trim().removePrefix("0x").removePrefix("0X")
        return cleaned.toLongOrNull(16)?.toInt() ?: cleaned.toIntOrNull()
    }

    private fun normalizeTrigger(raw: Int): Float =
        when {
            raw in 0..255 -> raw / 255f
            raw in 0..65535 -> raw / 65535f
            raw < 0 -> 0f
            else -> raw.coerceIn(0, 255) / 255f
        }

    private fun normalizeStick(raw: Int): Float =
        when {
            raw in -32768..32767 -> (raw / 32767f).coerceIn(-1f, 1f)
            raw in 0..255 -> ((raw - 127.5f) / 127.5f).coerceIn(-1f, 1f)
            raw in 0..65535 -> ((raw - 32767.5f) / 32767.5f).coerceIn(-1f, 1f)
            else -> raw.toFloat().coerceIn(-1f, 1f)
        }

    private fun isDpad(input: XboxInput): Boolean = input == XboxInput.DPAD_UP ||
        input == XboxInput.DPAD_DOWN || input == XboxInput.DPAD_LEFT || input == XboxInput.DPAD_RIGHT

    private fun buildNotification(): Notification {
        val channelId = "gamepad_bridge"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channelId, "Gamepad Bridge", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gamepad Bridge ativo")
            .setContentText("Lendo o controle e enviando entradas no padrão Xbox")
            .setSmallIcon(android.R.drawable.ic_input_get)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
        fun start(context: Context) {
            val intent = Intent(context, GamepadBridgeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, GamepadBridgeService::class.java))
        }
    }
}
