package com.gamepadbridge.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamepadbridge.app.data.ControllerProfile
import com.gamepadbridge.app.data.ProfileStore
import com.gamepadbridge.app.data.RawSource
import com.gamepadbridge.app.data.XboxInput
import com.gamepadbridge.app.input.CalibrationCapture
import com.gamepadbridge.app.input.DeviceNodeReader
import kotlinx.coroutines.launch

@Composable
fun MappingScreen(profileStore: ProfileStore, onDone: () -> Unit) {
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < 900L) onDone()
        else {
            lastBackAt = now
            Toast.makeText(context, "Aperte Voltar mais uma vez para ir ao menu principal", Toast.LENGTH_SHORT).show()
        }
    }
    var devices by remember { mutableStateOf<List<DeviceNodeReader.DeviceEntry>>(emptyList()) }
    var selectedNode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { devices = DeviceNodeReader.listDevices() }
    if (selectedNode == null) DeviceSelectStep(devices, onRefresh = { devices = DeviceNodeReader.listDevices() }, onSelected = { selectedNode = it })
    else CalibrationStep(node = selectedNode!!, profileStore = profileStore, onDone = onDone)
}

@Composable
private fun DeviceSelectStep(devices: List<DeviceNodeReader.DeviceEntry>, onRefresh: () -> Unit, onSelected: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Qual é o seu controle?", style = MaterialTheme.typography.headlineSmall)
        Text("Conecte o controle via OTG e escolha o nome dele na lista.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRefresh) { Text("Atualizar lista") }
        LazyColumn(Modifier.weight(1f)) {
            items(devices) { device ->
                ListItem(headlineContent = { Text(device.name) }, supportingContent = { Text(device.node) },
                    trailingContent = { Button(onClick = { onSelected(device.node) }) { Text("Usar") } },
                    modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CalibrationStep(node: String, profileStore: ProfileStore, onDone: () -> Unit) {
    val order = XboxInput.mappingOrder
    var index by remember { mutableIntStateOf(0) }
    val collected = remember { mutableStateMapOf<XboxInput, RawSource>() }
    val scope = rememberCoroutineScope()
    DisposableEffect(node) {
        CalibrationCapture.start(node)
        onDispose { CalibrationCapture.stop() }
    }
    LaunchedEffect(index) {
        if (index >= order.size) return@LaunchedEffect
        CalibrationCapture.events.collect { source ->
            if (collected.values.any { it == source }) return@collect
            collected[order[index]] = source
            index++
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mapeamento do controle", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        if (index < order.size) {
            val current = order[index]
            LinearProgressIndicator(progress = index.toFloat() / order.size, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("APERTE OU MOVA AGORA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(current.label, style = MaterialTheme.typography.titleLarge)
                    Text("Entrada ${index + 1} de ${order.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            XboxControllerVisual(highlight = current, modifier = Modifier.fillMaxWidth())
            Text("O botão pedido fica marcado com um círculo azul no desenho. Procure a mesma letra ou posição no seu controle.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { index++ }, modifier = Modifier.fillMaxWidth()) { Text("Pular este botão") }
        } else {
            Text("Mapeamento concluído!", style = MaterialTheme.typography.titleLarge)
            Text("${collected.size} entradas registradas • ${order.size - collected.size} ignoradas")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { scope.launch { profileStore.save(ControllerProfile(node, collected.toMap())); onDone() } },
                modifier = Modifier.fillMaxWidth()) { Text("Salvar perfil e voltar ao menu") }
        }
    }
}
