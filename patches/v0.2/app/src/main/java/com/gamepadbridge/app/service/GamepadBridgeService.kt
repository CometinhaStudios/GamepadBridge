package com.gamepadbridge.app.service

import android.app.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.concurrent.thread

class GamepadBridgeService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        val profile: ControllerProfile = ProfileStore(this).profileFlow.first() ?: return stopSelf()
        val keyMap = HashMap<String, XboxInput>()
        val axisMap = HashMap<String, Pair<XboxInput, Boolean>>()
        for ((xbox, source) in profile.mapping) {
            when (source) {
                is RawSource.Key -> keyMap[source.code] = xbox
                is RawSource.Axis -> axisMap[source.code] = xbox to source.positiveDirection
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
        axisMap: Map<String, Pair<XboxInput, Boolean>>
    ) {
        val source = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        when (line.type) {
            "EV_KEY" -> {
                val xbox = keyMap[line.code] ?: return
                val keyCode = ControllerProfile.outputKeyCode(xbox) ?: return
                val down = line.value.trim() == "DOWN"
                ShizukuBridge.injectKey(keyCode, down, source)
            }
            "EV_ABS" -> {
                val (xbox, _) = axisMap[line.code] ?: return
                val axis = ControllerProfile.outputAxis(xbox) ?: return
                val raw = line.value.trim().toIntOrNull(16) ?: return
                val isTrigger = xbox == XboxInput.L2 || xbox == XboxInput.R2
                val normalized = if (isTrigger) raw.coerceIn(0, 255) / 255f
                    else (raw.coerceIn(0, 255) / 255f) * 2f - 1f
                ShizukuBridge.injectAxisBatch(intArrayOf(axis), floatArrayOf(normalized), source)
            }
        }
    }

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
            .setContentText("Controle sendo traduzido para o layout Xbox")
            .setSmallIcon(android.R.drawable.ic_input_get)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
        fun start(context: Context) { ContextCompat_startForegroundService(context) }
        fun stop(context: Context) { context.stopService(Intent(context, GamepadBridgeService::class.java)) }
        private fun ContextCompat_startForegroundService(context: Context) {
            val intent = Intent(context, GamepadBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
