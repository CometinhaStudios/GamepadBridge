package com.gamepadbridge.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamepadbridge.app.data.ProfileStore
import com.gamepadbridge.app.inject.ShizukuBridge
import com.gamepadbridge.app.service.GamepadBridgeService
import com.gamepadbridge.app.ui.MappingScreen
import com.gamepadbridge.app.ui.ControllerTestScreen
import com.gamepadbridge.app.input.ControllerTestState
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    @Volatile private var mappingActive = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mappingActive && (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_B)) return true
        if (ControllerTestState.enabled &&
            (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0 &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            ControllerTestState.key(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ControllerTestState.enabled &&
            (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0) {
            val rx = event.getAxisValue(MotionEvent.AXIS_RX).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RY).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RZ)
            ControllerTestState.axes(
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                rx, ry, event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            )
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) ShizukuBridge.bind(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        if (ShizukuBridge.hasPermission) ShizukuBridge.bind(this)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val profileStore = remember { ProfileStore(applicationContext) }
                val profile by profileStore.profileFlow.collectAsState(initial = null)
                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            hasProfile = profile != null,
                            onTest = { navController.navigate("test") },
                            onMap = { navController.navigate("mapping") },
                            onToggleService = { starting ->
                                if (starting) GamepadBridgeService.start(this@MainActivity)
                                else GamepadBridgeService.stop(this@MainActivity)
                            }
                        )
                    }
                    composable("mapping") {
                        DisposableEffect(Unit) { mappingActive = true; onDispose { mappingActive = false } }
                        MappingScreen(profileStore = profileStore) { navController.popBackStack() }
                    }
                    composable("test") { ControllerTestScreen { navController.popBackStack() } }
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}

@Composable
fun HomeScreen(hasProfile: Boolean, onMap: () -> Unit, onToggleService: (Boolean) -> Unit, onTest: () -> Unit) {
    var serviceRunning by remember { mutableStateOf(false) }
    var shizukuOk by remember { mutableStateOf(ShizukuBridge.hasPermission) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gamepad Bridge", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Transforma seu controle genérico (OTG) em um Xbox Controller reconhecido pelos jogos do Android.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        if (!ShizukuBridge.isAvailable) {
            Text("Shizuku não encontrado. Instale e inicie o app Shizuku primeiro.")
        } else if (!shizukuOk) {
            Button(onClick = { ShizukuBridge.requestPermission(); shizukuOk = ShizukuBridge.hasPermission }) {
                Text("Autorizar via Shizuku")
            }
        } else {
            Button(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                Text(if (hasProfile) "Remapear controle" else "Mapear controle")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text("Testar controle") }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { serviceRunning = !serviceRunning; onToggleService(serviceRunning) },
                enabled = hasProfile, modifier = Modifier.fillMaxWidth()
            ) { Text(if (serviceRunning) "Parar ponte" else "Iniciar ponte") }
            if (!hasProfile) {
                Spacer(Modifier.height(8.dp))
                Text("Mapeie o controle primeiro.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
