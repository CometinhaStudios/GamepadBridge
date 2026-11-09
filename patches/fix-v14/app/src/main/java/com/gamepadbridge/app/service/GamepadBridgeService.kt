package com.gamepadbridge.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.InputDevice
import androidx.core.app.NotificationCompat
import com.gamepadbridge.app.data.ControllerProfile
import com.gamepadbridge.app.data.ProfileStore
import com.gamepadbridge.app.data.RawSource
import com.gamepadbridge.app.data.XboxInput
import com.gamepadbridge.app.inject.ShizukuBridge
import com.gamepadbridge.app.input.DeviceNodeReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class GamepadBridgeService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var running = false
    @Volatile private var activeNode: String? = null
    private val axisValues = HashMap<Int, Float>()
    private val pressedKeyCodes = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (!running) scope.launch { runBridge() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        activeNode?.let(DeviceNodeReader::stop)
        val keysToRelease = synchronized(pressedKeyCodes) {
            pressedKeyCodes.toList().also { pressedKeyCodes.clear() }
        }
        keysToRelease.forEach { ShizukuBridge.injectKey(it, false, InputDevice.SOURCE_GAMEPAD) }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runBridge() {
        val profile = ProfileStore(this).activeControllerProfile() ?: run {
            stopSelf()
            return
        }
        if (!ShizukuBridge.hasPermission) {
            stopSelf()
            return
        }

        val keyMap = HashMap<String, XboxInput>()
        val axisMap = HashMap<String, MutableList<Pair<XboxInput, Boolean>>>()
        profile.mapping.forEach { (output, input) ->
            when (input) {
                is RawSource.Key -> keyMap[input.code] = output
                is RawSource.Axis -> axisMap.getOrPut(input.code) { mutableListOf() }
                    .add(output to input.positiveDirection)
            }
        }

        val axisRanges = DeviceNodeReader.getAxisRanges(profile.deviceName)
        val mappedAxes = axisMap.values.flatten()
            .mapNotNull { ControllerProfile.outputAxis(it.first) }
            .distinct()
        synchronized(axisValues) {
            axisValues.clear()
            mappedAxes.forEach { axisValues[it] = 0f }
        }

        running = true
        activeNode = profile.deviceName
        thread(name = "gamepad-bridge-reader") {
            DeviceNodeReader.streamEvents(
                node = profile.deviceName,
                onLine = { line -> handleLine(line, keyMap, axisMap, axisRanges, mappedAxes) },
                shouldStop = { !running }
            )
            if (running) stopSelf()
        }
    }

    private fun handleLine(
        line: DeviceNodeReader.RawLine,
        keyMap: Map<String, XboxInput>,
        axisMap: Map<String, List<Pair<XboxInput, Boolean>>>,
        axisRanges: Map<String, DeviceNodeReader.AxisRange>,
        mappedAxes: List<Int>
    ) {
        when (line.type) {
            "EV_KEY" -> {
                val xbox = keyMap[line.code] ?: return
                val keyCode = ControllerProfile.outputKeyCode(xbox) ?: return
                when (line.value.trim()) {
                    "DOWN" -> {
                        val isNewPress = synchronized(pressedKeyCodes) { pressedKeyCodes.add(keyCode) }
                        if (isNewPress) ShizukuBridge.injectKey(keyCode, true, InputDevice.SOURCE_GAMEPAD)
                    }
                    "UP" -> {
                        val wasPressed = synchronized(pressedKeyCodes) { pressedKeyCodes.remove(keyCode) }
                        if (wasPressed) ShizukuBridge.injectKey(keyCode, false, InputDevice.SOURCE_GAMEPAD)
                    }
                }
            }
            "EV_ABS" -> {
                val mappings = axisMap[line.code] ?: return
                val raw = parseRawValue(line.value) ?: return
                val range = axisRanges[line.code]

                for ((xbox, positiveDirection) in mappings) {
                    val axis = ControllerProfile.outputAxis(xbox) ?: continue
                    val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2
                    val normalized = when {
                        range != null && isTrigger -> range.normalizeTrigger(raw)
                        range != null -> range.normalize(raw)
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

                val axes = mappedAxes.toIntArray()
                val values = synchronized(axisValues) {
                    FloatArray(axes.size) { index -> axisValues[axes[index]] ?: 0f }
                }
                ShizukuBridge.injectAxisBatch(axes, values, InputDevice.SOURCE_JOYSTICK)
            }
        }
    }

    private fun parseRawValue(value: String): Int? = runCatching {
        value.trim().removePrefix("0x").toLong(16).toInt()
    }.getOrNull()

    private fun buildNotification(): Notification {
        val channelId = "gamepad_bridge"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Gamepad Bridge", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gamepad Bridge ativo")
            .setContentText("Perfil de controle sendo aplicado em segundo plano")
            .setSmallIcon(android.R.drawable.ic_input_get)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, GamepadBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GamepadBridgeService::class.java))
        }
    }
}
