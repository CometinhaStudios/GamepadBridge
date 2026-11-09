package com.gamepadbridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamepadbridge.app.input.ControllerTestState

@Composable
fun ControllerTestScreen(onBack: () -> Unit) {
    DisposableEffect(Unit) {
        ControllerTestState.enabled = true
        onDispose { ControllerTestState.enabled = false; ControllerTestState.pressed.clear() }
    }
    val pressed = ControllerTestState.pressed.keys.toSet()
    val lx = ControllerTestState.leftX
    val ly = ControllerTestState.leftY
    val rx = ControllerTestState.rightX
    val ry = ControllerTestState.rightY
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Teste de controle", style = MaterialTheme.typography.headlineSmall)
        Text("Aperte os botões e mova os analógicos para conferir a resposta.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        XboxControllerVisual(null, Modifier.fillMaxWidth(), pressed, lx, ly, rx, ry)
        Spacer(Modifier.height(8.dp))
        Text("Botões: ${if (pressed.isEmpty()) "nenhum" else pressed.joinToString { it.label }}")
        Text("Analógico esquerdo: X ${"%.2f".format(lx)} • Y ${"%.2f".format(ly)}")
        Text("Analógico direito: X ${"%.2f".format(rx)} • Y ${"%.2f".format(ry)}")
        Text("Gatilhos: LT ${"%.2f".format(ControllerTestState.lt)} • RT ${"%.2f".format(ControllerTestState.rt)}")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Voltar") }
    }
}
