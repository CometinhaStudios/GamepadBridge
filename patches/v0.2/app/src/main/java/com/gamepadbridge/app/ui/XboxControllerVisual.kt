package com.gamepadbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gamepadbridge.app.R
import com.gamepadbridge.app.data.XboxInput

@Composable
fun XboxControllerVisual(
    highlight: XboxInput?,
    modifier: Modifier = Modifier,
    pressed: Set<XboxInput> = emptySet(),
    leftX: Float = 0f, leftY: Float = 0f, rightX: Float = 0f, rightY: Float = 0f
) {
    Box(modifier.fillMaxWidth().aspectRatio(1.1f)) {
        Image(
            painter = painterResource(R.drawable.xbox360_white),
            contentDescription = "Controle branco Xbox 360",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val active = Color(0xFFFF8A00)
            fun ring(input: XboxInput, x: Float, y: Float, radius: Float) {
                if (input == highlight || input in pressed) drawCircle(active, radius, Offset(w*x, h*y), style = Stroke(8f))
            }
            ring(XboxInput.LS_X, .35f, .23f, w*.055f)
            ring(XboxInput.RS_X, .66f, .60f, w*.055f)
            ring(XboxInput.DPAD_UP, .40f, .47f, w*.075f)
            ring(XboxInput.Y, .89f, .38f, w*.035f)
            ring(XboxInput.B, .94f, .52f, w*.035f)
            ring(XboxInput.A, .83f, .61f, w*.035f)
            ring(XboxInput.X, .78f, .48f, w*.035f)
            if (leftX != 0f || leftY != 0f) drawCircle(active, w*.012f, Offset(w*(.35f + leftX*.045f), h*(.23f + leftY*.045f)))
            if (rightX != 0f || rightY != 0f) drawCircle(active, w*.012f, Offset(w*(.66f + rightX*.045f), h*(.60f + rightY*.045f)))
        }
    }
}
