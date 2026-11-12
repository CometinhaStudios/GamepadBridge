package com.gamepadbridge.preview

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

private enum class GamepadButton {
    A, B, X, Y,
    LB, RB, LT, RT,
    L3, R3,
    START, BACK,
    UP, DOWN, LEFT, RIGHT
}

private data class GamepadState(
    val buttons: Set<GamepadButton> = emptySet(),
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val deviceName: String = ""
)

class MainActivity : ComponentActivity() {

    private val gamepadState = MutableStateFlow(GamepadState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8FC43A)
                )
            ) {
                val currentState by gamepadState.collectAsState()
                var currentPage by remember { mutableIntStateOf(0) }

                when (currentPage) {
                    0 -> HomeScreen(
                        state = currentState,
                        onOpenTest = { currentPage = 1 },
                        onOpenMapping = { currentPage = 2 }
                    )

                    1 -> ControllerTestScreen(
                        state = currentState,
                        onBack = { currentPage = 0 }
                    )

                    else -> MappingPreviewScreen(
                        state = currentState,
                        onBack = { currentPage = 0 }
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButton.A
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButton.B
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButton.X
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButton.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButton.LB
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButton.RB
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButton.LT
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButton.RT
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButton.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButton.R3
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButton.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButton.BACK
            KeyEvent.KEYCODE_DPAD_UP -> GamepadButton.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadButton.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadButton.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadButton.RIGHT
            else -> null
        }

        if (button == null) {
            return super.dispatchKeyEvent(event)
        }

        val updatedButtons = when (event.action) {
            KeyEvent.ACTION_DOWN -> gamepadState.value.buttons + button
            KeyEvent.ACTION_UP -> gamepadState.value.buttons - button
            else -> return super.dispatchKeyEvent(event)
        }

        val current = gamepadState.value
        gamepadState.value = current.copy(
            buttons = updatedButtons,
            deviceName = event.device?.name ?: current.deviceName.ifBlank { "Gamepad" }
        )
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val isGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)

        if (event.action != MotionEvent.ACTION_MOVE || !isGamepad) {
            return super.dispatchGenericMotionEvent(event)
        }

        val current = gamepadState.value
        val zAxis = event.getAxisValue(MotionEvent.AXIS_Z)
        val rzAxis = event.getAxisValue(MotionEvent.AXIS_RZ)

        gamepadState.value = current.copy(
            leftX = event.getAxisValue(MotionEvent.AXIS_X).coerceIn(-1f, 1f),
            leftY = event.getAxisValue(MotionEvent.AXIS_Y).coerceIn(-1f, 1f),
            rightX = (if (abs(zAxis) > 0.001f) zAxis
                else event.getAxisValue(MotionEvent.AXIS_RX)).coerceIn(-1f, 1f),
            rightY = (if (abs(rzAxis) > 0.001f) rzAxis
                else event.getAxisValue(MotionEvent.AXIS_RY)).coerceIn(-1f, 1f),
            leftTrigger = readTrigger(
                event,
                MotionEvent.AXIS_LTRIGGER,
                MotionEvent.AXIS_BRAKE
            ),
            rightTrigger = readTrigger(
                event,
                MotionEvent.AXIS_RTRIGGER,
                MotionEvent.AXIS_GAS
            ),
            deviceName = event.device?.name ?: current.deviceName.ifBlank { "Gamepad" }
        )
        return true
    }

    private fun readTrigger(event: MotionEvent, primaryAxis: Int, fallbackAxis: Int): Float {
        val primaryValue = event.getAxisValue(primaryAxis)
        val value = if (primaryValue > 0.001f) {
            primaryValue
        } else {
            event.getAxisValue(fallbackAxis)
        }
        return value.coerceIn(0f, 1f)
    }
}

private val AccentGreen = Color(0xFF8FC43A)
private val ScreenBackground = Color(0xFF09120C)
private val PanelBackground = Color(0xFF1B2A20)
private val SecondaryText = Color(0xFFB9C4AE)

@Composable
private fun ScreenShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF13251A), ScreenBackground)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun ScreenHeader() {
    Text(
        text = "GAMEPAD BRIDGE",
        fontSize = 23.sp,
        color = Color.White,
        fontWeight = FontWeight.Black
    )
    Text(
        text = "DASHBOARD XBOX • PRÉVIA",
        fontSize = 10.sp,
        color = AccentGreen
    )
}

@Composable
private fun ActionTile(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = if (highlighted) AccentGreen else PanelBackground,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (highlighted) Color.Black else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ControllerImage(height: Int) {
    Image(
        painter = painterResource(id = R.drawable.controller_xbox360),
        contentDescription = "Ilustração de um controle estilo Xbox 360",
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun HomeScreen(
    state: GamepadState,
    onOpenTest: () -> Unit,
    onOpenMapping: () -> Unit
) {
    ScreenShell {
        ScreenHeader()
        Spacer(modifier = Modifier.height(35.dp))

        Text(
            text = "SEU CONTROLE.",
            fontSize = 28.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "SEU JEITO DE JOGAR.",
            fontSize = 23.sp,
            color = AccentGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (state.deviceName.isBlank()) {
                "Conecte seu controle"
            } else {
                "CONTROLE: ${state.deviceName}"
            },
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(20.dp))
        ControllerImage(height = 200)
        ActionTile(
            text = "TESTAR CONTROLE",
            onClick = onOpenTest,
            highlighted = true
        )
        ActionTile(
            text = "MAPEAMENTO VISUAL",
            onClick = onOpenMapping
        )
        Text(
            text = "Botões • analógicos • gatilhos em tempo real",
            color = SecondaryText
        )
    }
}

@Composable
private fun ControllerTestScreen(
    state: GamepadState,
    onBack: () -> Unit
) {
    ScreenShell {
        Text(
            text = "‹ VOLTAR",
            color = AccentGreen,
            modifier = Modifier.clickable(onClick = onBack)
        )
        Text(
            text = "TESTE DE CONTROLE",
            fontSize = 25.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.deviceName.ifBlank { "Aguardando entrada..." },
            color = SecondaryText
        )

        ControllerImage(height = 205)

        Text(
            text = "BOTÕES: ${state.buttons.joinToString()}",
            color = AccentGreen
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "ANALÓGICOS",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        ValueMeter("ESQUERDO X", state.leftX)
        ValueMeter("ESQUERDO Y", state.leftY)
        ValueMeter("DIREITO X", state.rightX)
        ValueMeter("DIREITO Y", state.rightY)

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "GATILHOS",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        ValueMeter("LT", state.leftTrigger, isTrigger = true)
        ValueMeter("RT", state.rightTrigger, isTrigger = true)
    }
}

@Composable
private fun ValueMeter(
    label: String,
    value: Float,
    isTrigger: Boolean = false
) {
    val progress = if (isTrigger) value else (value + 1f) / 2f

    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = SecondaryText)
            Text(
                text = "${(value * 100).toInt()}%",
                color = Color.White
            )
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = AccentGreen
        )
    }
}

@Composable
private fun MappingPreviewScreen(
    state: GamepadState,
    onBack: () -> Unit
) {
    ScreenShell {
        ScreenHeader()
        Text(
            text = "MAPEAMENTO DO CONTROLE",
            fontSize = 22.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tela de prévia do mapeamento",
            color = SecondaryText
        )

        ControllerImage(height = 220)

        Text(
            text = "Entradas recebidas: ${state.buttons.joinToString()}",
            color = AccentGreen
        )

        ActionTile(
            text = "VOLTAR",
            onClick = onBack,
            highlighted = true
        )
    }
}
