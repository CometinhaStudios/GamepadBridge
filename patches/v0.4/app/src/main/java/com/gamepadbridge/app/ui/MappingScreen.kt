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
    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < 900L) onDone()
        else {
            lastBackAt = now
            Toast.makeText(context, "Aperte Voltar novamente para sair do mapeamento", Toast.LENGTH_SHORT).show()
        }
    }
    var devices by remember { mutableStateOf<List<DeviceNodeReader.DeviceEntry>>(emptyList()) }
    var selectedNode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { devices = DeviceNodeReader.listDevices() }
    if (selectedNode == null) {
        DeviceSelectStep(devices, onRefresh = { devices = DeviceNodeReader.listDevices() }, onSelected = { selectedNode = it })
    } else {
        CalibrationStep(selectedNode!!, profileStore, onDone)
    }
}

@Composable
private fun DeviceSelectStep(
    devices: List<DeviceNodeReader.DeviceEntry>,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Qual é o seu controle?", style = MaterialTheme.typography.headlineSmall)
        Text("Conecte o controle via OTG e escolha o dispositivo correto.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRefresh) { Text("Atualizar lista") }
        LazyColumn(Modifier.weight(1f)) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(device.node) },
                    trailingContent = { Button(onClick = { onSelected(device.node) }) { Text("Usar") } },
                    modifier = Modifier.fillMaxWidth()
                )
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
    var candidate by remember { mutableStateOf<RawSource?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(node) {
        CalibrationCapture.start(node)
        onDispose { CalibrationCapture.stop() }
    }

    // A leitura para assim que encontra uma entrada. Nada é salvo até o usuário confirmar.
    LaunchedEffect(index, candidate) {
        if (index >= order.size || candidate != null) return@LaunchedEffect
        CalibrationCapture.events.collect { source ->
            if (candidate == null) {
                if (collected.values.any { it == source }) {
                    warning = "Essa entrada já foi usada. Aperte outro botão ou mova outra direção."
                } else {
                    candidate = source
                    warning = null
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mapeamento do controle", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (index < order.size) {
            val current = order[index]
            LinearProgressIndicator(
                progress = index.toFloat() / order.size,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ENTRADA ATUAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(current.label, style = MaterialTheme.typography.titleLarge)
                    Text("Etapa ${index + 1} de ${order.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            XboxControllerVisual(highlight = current, modifier = Modifier.fillMaxWidth())
            Text(
                "Aperte somente o botão indicado. Para o direcional, pressione cada direção separadamente.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            if (candidate == null) {
                Text("Aguardando botão ou direção…", style = MaterialTheme.typography.titleMedium)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ENTRADA DETECTADA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(sourceLabel(candidate!!), style = MaterialTheme.typography.titleMedium)
                        Text("Confira se foi exatamente o botão solicitado antes de confirmar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val found = candidate ?: return@Button
                        if (collected.values.any { it == found }) {
                            warning = "Essa entrada já está atribuída a outro botão."
                            candidate = null
                        } else {
                            collected[current] = found
                            candidate = null
                            warning = null
                            index++
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirmar tecla pressionada") }
                OutlinedButton(onClick = { candidate = null; warning = "Não foi essa tecla. Tente novamente." }, modifier = Modifier.fillMaxWidth()) {
                    Text("Não foi essa — tentar novamente")
                }
            }
            if (warning != null) {
                Text(warning!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (index > 0) {
                            index--
                            collected.remove(order[index])
                            candidate = null
                            warning = null
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Anterior") }
                OutlinedButton(
                    onClick = { candidate = null; warning = null; index++ },
                    modifier = Modifier.weight(1f)
                ) { Text("Pular") }
            }
        } else {
            Text("Mapeamento concluído!", style = MaterialTheme.typography.titleLarge)
            Text("${collected.size} entradas registradas • ${order.size - collected.size} ignoradas")
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        profileStore.save(ControllerProfile(node, collected.toMap()))
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar perfil e voltar ao menu") }
            OutlinedButton(
                onClick = { index = order.lastIndex; candidate = null },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Voltar e revisar") }
        }
    }
}

private fun sourceLabel(source: RawSource): String = when (source) {
    is RawSource.Key -> "Botão físico • código ${source.code}"
    is RawSource.Axis -> "Eixo ${source.code} • direção ${if (source.positiveDirection) "+" else "−"}"
}
