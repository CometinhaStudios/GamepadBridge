package com.gamepadbridge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    BackHandler(enabled = true) { /* Círculo/B não fecha o mapeamento. */ }
    var devices by remember { mutableStateOf<List<DeviceNodeReader.DeviceEntry>>(emptyList()) }
    var selectedNode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { devices = DeviceNodeReader.listDevices() }
    if (selectedNode == null) {
        DeviceSelectStep(devices, onRefresh = { devices = DeviceNodeReader.listDevices() }, onSelected = { selectedNode = it })
    } else {
        CalibrationStep(node = selectedNode!!, profileStore = profileStore, onDone = onDone)
    }
}

@Composable
private fun DeviceSelectStep(devices: List<DeviceNodeReader.DeviceEntry>, onRefresh: () -> Unit, onSelected: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Qual é o seu controle?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Conecte o controle via OTG e escolha o nome dele na lista abaixo.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) { Text("Atualizar lista") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(device.node) },
                    trailingContent = { Button(onClick = { onSelected(device.node) }) { Text("Usar") } },
                    modifier = Modifier.fillMaxWidth()
                )
                Divider()
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
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("Mapeamento do controle", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (index < order.size) {
            val current = order[index]
            Text("Pressione / mova: ${current.label}", style = MaterialTheme.typography.titleMedium)
            Text("${index + 1} de ${order.size}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            XboxControllerVisual(highlight = current, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { index++ }) { Text("Pular esse botão") }
        } else {
            Text("Mapeamento concluído!")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    profileStore.save(ControllerProfile(node, collected.toMap()))
                    onDone()
                }
            }) { Text("Salvar perfil") }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "O desenho mostra a posição física do botão pedido. Se não quiser mapear uma entrada, use Pular esse botão.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
