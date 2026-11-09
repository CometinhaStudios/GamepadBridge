package com.gamepadbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gamepadbridge.app.R
import com.gamepadbridge.app.data.XboxInput

// Usa a imagem real do controle Xbox 360 como base; o destaque é desenhado por cima.
@Composable
fun XboxControllerVisual(
    highlight: XboxInput?,
    modifier: Modifier = Modifier,
    pressed: Set<XboxInput> = emptySet(),
    leftX: Float = 0f,
    leftY: Float = 0f,
    rightX: Float = 0f,
    rightY: Float = 0f
) {
    Box(modifier.fillMaxWidth().aspectRatio(1.1f)) {
        Image(
            painter = painterResource(R.drawable.xbox360_white),
            contentDescription = "Controle Xbox 360 branco",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val active = Color(0xFF168BFF)
            fun mark(inputs: Set<XboxInput>, x: Float, y: Float, radius: Float) {
                if (highlight in inputs || inputs.any { it in pressed }) {
                    drawCircle(active.copy(alpha = .20f), radius * 1.75f, Offset(w * x, h * y))
                    drawCircle(active, radius, Offset(w * x, h * y), style = Stroke(w * .012f))
                }
            }
            // Coordenadas alinhadas à foto oficial usada como fundo.
            mark(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.L3), .35f, .23f, w * .055f)
            mark(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.R3), .66f, .60f, w * .055f)
            mark(setOf(XboxInput.DPAD_UP, XboxInput.DPAD_DOWN, XboxInput.DPAD_LEFT, XboxInput.DPAD_RIGHT), .40f, .47f, w * .075f)
            mark(setOf(XboxInput.Y), .89f, .38f, w * .035f)
            mark(setOf(XboxInput.B), .94f, .52f, w * .035f)
            mark(setOf(XboxInput.A), .83f, .61f, w * .035f)
            mark(setOf(XboxInput.X), .78f, .48f, w * .035f)
            mark(setOf(XboxInput.L1), .20f, .10f, w * .045f)
            mark(setOf(XboxInput.R1), .82f, .17f, w * .045f)
            mark(setOf(XboxInput.L2), .17f, .06f, w * .045f)
            mark(setOf(XboxInput.R2), .85f, .12f, w * .045f)
            mark(setOf(XboxInput.BACK), .49f, .36f, w * .025f)
            mark(setOf(XboxInput.START), .67f, .40f, w * .025f)
            if (leftX != 0f || leftY != 0f) {
                drawCircle(active, w * .012f, Offset(w * (.35f + leftX * .045f), h * (.23f + leftY * .045f)))
            }
            if (rightX != 0f || rightY != 0f) {
                drawCircle(active, w * .012f, Offset(w * (.66f + rightX * .045f), h * (.60f + rightY * .045f)))
            }
        }
    }
}
