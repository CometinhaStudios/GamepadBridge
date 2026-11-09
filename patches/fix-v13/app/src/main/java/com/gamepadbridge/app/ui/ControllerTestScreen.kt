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
        onDispose {
            ControllerTestState.enabled = false
            ControllerTestState.pressed.clear()
        }
    }
    val pressed = ControllerTestState.pressed.keys.toSet()
    val lx = ControllerTestState.leftX
    val ly = ControllerTestState.leftY
    val rx = ControllerTestState.rightX
    val ry = ControllerTestState.rightY
    val lt = ControllerTestState.lt
    val rt = ControllerTestState.rt

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Teste de controle", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Aperte os botões, mova os analógicos e pressione os gatilhos.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        XboxControllerVisual(
            highlight = null,
            modifier = Modifier.fillMaxWidth(),
            pressed = pressed,
            leftX = lx,
            leftY = ly,
            rightX = rx,
            rightY = ry,
            leftTrigger = lt,
            rightTrigger = rt
        )
        Spacer(Modifier.height(8.dp))
        Text("Botões: ${if (pressed.isEmpty()) "nenhum" else pressed.joinToString { it.label }}")
        Text("Analógico esquerdo: X ${"%.2f".format(lx)} • Y ${"%.2f".format(ly)}")
        Text("Analógico direito: X ${"%.2f".format(rx)} • Y ${"%.2f".format(ry)}")
        Text("Gatilhos: LT ${"%.0f".format(lt * 100)}% • RT ${"%.0f".format(rt * 100)}%")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}
