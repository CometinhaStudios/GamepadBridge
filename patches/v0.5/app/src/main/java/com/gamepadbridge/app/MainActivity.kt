package com.gamepadbridge.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    @Volatile private var mappingActive = false
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mappingActive && event.keyCode == KeyEvent.KEYCODE_BUTTON_B) return true
        val dpad = event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        val gamepad = (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD)) != 0
        if (ControllerTestState.enabled && (gamepad || dpad) && event.action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            ControllerTestState.key(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ControllerTestState.enabled && (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0) {
            val rx = event.getAxisValue(MotionEvent.AXIS_RX).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RY).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RZ)
            ControllerTestState.axes(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), rx, ry,
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_RTRIGGER))
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
                val nav = rememberNavController()
                val store = remember { ProfileStore(applicationContext) }
                var pendingProfileName by remember { mutableStateOf<String?>(null) }
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        val profiles by store.profilesFlow.collectAsState(initial = emptyMap())
                        val active by store.activeProfileNameFlow.collectAsState(initial = null)
                        HomeScreen(profiles.isNotEmpty(), active, { nav.navigate("test") }, { nav.navigate("profiles") }) { start ->
                            if (start) GamepadBridgeService.start(this@MainActivity) else GamepadBridgeService.stop(this@MainActivity)
                        }
                    }
                    composable("profiles") {
                        ProfileManagerScreen(store, onMap = { name ->
                            pendingProfileName = name
                            nav.navigate("mapping")
                        }, onBack = { nav.popBackStack("home", false) })
                    }
                    composable("mapping") {
                        DisposableEffect(Unit) { mappingActive = true; onDispose { mappingActive = false } }
                        MappingScreen(store, profileName = pendingProfileName) {
                            pendingProfileName = null
                            nav.popBackStack("home", false)
                        }
                    }
                    composable("test") { ControllerTestScreen { nav.popBackStack("home", false) } }
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
private fun HomeScreen(hasProfile: Boolean, activeName: String?, onTest: () -> Unit, onProfiles: () -> Unit, onToggleService: (Boolean) -> Unit) {
    var serviceRunning by remember { mutableStateOf(false) }
    var shizukuOk by remember { mutableStateOf(ShizukuBridge.hasPermission) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Gamepad Bridge", style = MaterialTheme.typography.headlineMedium)
        Text("Transforma seu controle genérico (OTG) em um controle reconhecido como Xbox.")
        Spacer(Modifier.height(24.dp))
        if (!ShizukuBridge.isAvailable) Text("Shizuku não encontrado. Instale e inicie o Shizuku primeiro.")
        else if (!shizukuOk) Button(onClick = { ShizukuBridge.requestPermission(); shizukuOk = ShizukuBridge.hasPermission }) { Text("Autorizar via Shizuku") }
        else {
            Text(if (hasProfile) "Perfil ativo: ${activeName ?: "não selecionado"}" else "Nenhum perfil criado")
            Button(onClick = onProfiles, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar perfis") }
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text("Testar controle") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { serviceRunning = !serviceRunning; onToggleService(serviceRunning) }, enabled = hasProfile, modifier = Modifier.fillMaxWidth()) {
                Text(if (serviceRunning) "Parar ponte" else "Iniciar ponte")
            }
        }
    }
}

@Composable
private fun ProfileManagerScreen(store: ProfileStore, onMap: (String) -> Unit, onBack: () -> Unit) {
    val profiles by store.profilesFlow.collectAsState(initial = emptyMap())
    val active by store.activeProfileNameFlow.collectAsState(initial = null)
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Perfis de controle", style = MaterialTheme.typography.headlineSmall)
        Text("Crie um mapeamento para cada controle ou jogo e troque sem precisar mapear novamente.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nome do novo perfil") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { val name = newName.trim(); if (name.isNotEmpty()) onMap(name) }, enabled = newName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Criar perfil e mapear") }
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) Text("Ainda não há perfis salvos.")
        LazyColumn(Modifier.weight(1f)) {
            items(profiles.keys.toList()) { name ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(profiles[name]?.deviceName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { store.select(name) } }, enabled = active != name) { Text(if (active == name) "Ativo" else "Selecionar") }
                            OutlinedButton(onClick = { renameTarget = name; renameValue = name }) { Text("Renomear") }
                            OutlinedButton(onClick = { scope.launch { store.delete(name) } }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
    if (renameTarget != null) AlertDialog(
        onDismissRequest = { renameTarget = null },
        title = { Text("Renomear perfil") },
        text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, label = { Text("Nome") }) },
        confirmButton = { TextButton(onClick = { val old = renameTarget!!; val next = renameValue.trim(); scope.launch { store.rename(old, next) }; renameTarget = null }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
    )
}
