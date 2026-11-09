package com.gamepadbridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamepadbridge.app.input.ControllerTestState

@Composable
fun ControllerTestScreen(onBack: () -> Unit) {
    val pressed = ControllerTestState.pressed.toMap()
    val axes = ControllerTestState.axes.value
    val active = pressed.filterValues { it }.keys

    DisposableEffect(Unit) {
        ControllerTestState.enabled = true
        ControllerTestState.clear()
        onDispose {
            ControllerTestState.enabled = false
            ControllerTestState.clear()
        }
    }

    val lx = axes.getOrElse(0) { 0f }.coerceIn(-1f, 1f)
    val ly = axes.getOrElse(1) { 0f }.coerceIn(-1f, 1f)
    val rx = axes.getOrElse(2) { 0f }.coerceIn(-1f, 1f)
    val ry = axes.getOrElse(3) { 0f }.coerceIn(-1f, 1f)
    val lt = axes.getOrElse(4) { 0f }.coerceIn(0f, 1f)
    val rt = axes.getOrElse(5) { 0f }.coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Text("Teste do controle", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Pressione os botões, mova os analógicos e aperte os gatilhos.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        XboxControllerVisual(
            highlight = ControllerTestState.highlight(),
            modifier = Modifier.fillMaxWidth(),
            pressed = active,
            leftX = lx,
            leftY = ly,
            rightX = rx,
            rightY = ry,
            leftTrigger = lt,
            rightTrigger = rt
        )

        Spacer(Modifier.height(12.dp))
        Text("Botões detectados", style = MaterialTheme.typography.titleMedium)
        Text(
            if (active.isEmpty()) "Nenhum botão pressionado"
            else active.joinToString(" • ") { android.view.KeyEvent.keyCodeToString(it) }
        )
        Spacer(Modifier.height(12.dp))
        Text("Movimento dos analógicos", style = MaterialTheme.typography.titleMedium)

        val axisRows = listOf(
            "Analógico esquerdo X" to lx,
            "Analógico esquerdo Y" to ly,
            "Analógico direito X" to rx,
            "Analógico direito Y" to ry
        )
        axisRows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = (value + 1f) / 2f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Gatilhos", style = MaterialTheme.typography.titleMedium)
        listOf("LT" to lt, "RT" to rt).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = value, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}
