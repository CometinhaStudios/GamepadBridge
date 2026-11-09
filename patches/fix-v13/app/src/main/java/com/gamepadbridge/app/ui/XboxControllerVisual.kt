package com.gamepadbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.gamepadbridge.app.data.XboxInput

/**
 * Xbox-style controller rendered in a shallow 3/4 view so the shoulder buttons
 * and analog triggers remain visible while mapping and during live testing.
 */
@Composable
fun XboxControllerVisual(
    highlight: XboxInput?,
    modifier: Modifier = Modifier,
    pressed: Set<XboxInput> = emptySet(),
    leftX: Float = 0f,
    leftY: Float = 0f,
    rightX: Float = 0f,
    rightY: Float = 0f,
    leftTrigger: Float = 0f,
    rightTrigger: Float = 0f
) {
    Canvas(modifier.fillMaxWidth().aspectRatio(1.62f)) {
        val sx = size.width / 1000f
        val sy = size.height / 618f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun circle(x: Float, y: Float, r: Float, color: Color, border: Color = Color(0xFF30383D), stroke: Float = 7f) {
            drawCircle(color, r * sx, p(x, y))
            drawCircle(border, r * sx, p(x, y), style = Stroke(stroke * sx))
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 14f) {
            drawRoundRect(color, p(x, y), Size(w * sx, h * sy), androidx.compose.ui.geometry.CornerRadius(radius * sx, radius * sy))
        }
        fun focus(inputs: Set<XboxInput>, x: Float, y: Float, r: Float) {
            if (highlight in inputs || inputs.any { it in pressed }) {
                drawCircle(Color(0xFFFFB52E).copy(alpha = .25f), r * 1.45f * sx, p(x, y))
                drawCircle(Color(0xFFFFB52E), r * sx, p(x, y), style = Stroke(8f * sx))
            }
        }
        val shell = Color(0xFFE7E9E8)
        val edge = Color(0xFF747D81)
        val dark = Color(0xFF303A40)
        val mid = Color(0xFF566168)
        val lime = Color(0xFFB9E52C)

        // Soft cast shadow and the rear/top plane create the raised perspective.
        drawRoundRect(Color.Black.copy(alpha = .24f), p(135f, 185f), Size(730f * sx, 365f * sy),
            androidx.compose.ui.geometry.CornerRadius(150f * sx, 150f * sy))
        val rear = Path().apply {
            moveTo(p(205f, 178f).x, p(205f, 178f).y)
            cubicTo(p(270f, 92f).x, p(270f, 92f).y, p(390f, 78f).x, p(390f, 78f).y, p(500f, 112f).x, p(500f, 112f).y)
            cubicTo(p(610f, 78f).x, p(610f, 78f).y, p(730f, 92f).x, p(730f, 92f).y, p(795f, 178f).x, p(795f, 178f).y)
            lineTo(p(205f, 178f).x, p(205f, 178f).y)
            close()
        }
        drawPath(rear, Color(0xFFB8BFC0))
        drawPath(rear, edge, style = Stroke(6f * sx))

        // Visible trigger paddles on the raised rear edge.
        rect(220f, 92f, 190f, 64f, Color(0xFF424D53), 22f)
        rect(590f, 92f, 190f, 64f, Color(0xFF424D53), 22f)
        rect(232f, 99f, 166f, 42f, Color(0xFF788287), 16f)
        rect(602f, 99f, 166f, 42f, Color(0xFF788287), 16f)
        val lt = leftTrigger.coerceIn(0f, 1f)
        val rt = rightTrigger.coerceIn(0f, 1f)
        rect(236f, 105f, 158f * lt, 30f, Color(0xFFFFB52E), 12f)
        rect(606f, 105f, 158f * rt, 30f, Color(0xFFFFB52E), 12f)

        // Main front shell, with the front edge lower than the trigger plane.
        val body = Path().apply {
            moveTo(p(210f, 166f).x, p(210f, 166f).y)
            cubicTo(p(300f, 145f).x, p(300f, 145f).y, p(405f, 175f).x, p(405f, 175f).y, p(500f, 192f).x, p(500f, 192f).y)
            cubicTo(p(595f, 175f).x, p(595f, 175f).y, p(700f, 145f).x, p(700f, 145f).y, p(790f, 166f).x, p(790f, 166f).y)
            cubicTo(p(865f, 190f).x, p(865f, 190f).y, p(920f, 360f).x, p(920f, 360f).y, p(850f, 505f).x, p(850f, 505f).y)
            cubicTo(p(805f, 585f).x, p(805f, 585f).y, p(735f, 565f).x, p(735f, 565f).y, p(665f, 492f).x, p(665f, 492f).y)
            cubicTo(p(600f, 430f).x, p(600f, 430f).y, p(400f, 430f).x, p(400f, 430f).y, p(335f, 492f).x, p(335f, 492f).y)
            cubicTo(p(265f, 565f).x, p(265f, 565f).y, p(195f, 585f).x, p(195f, 585f).y, p(150f, 505f).x, p(150f, 505f).y)
            cubicTo(p(80f, 360f).x, p(80f, 360f).y, p(135f, 190f).x, p(135f, 190f).y, p(210f, 166f).x, p(210f, 166f).y)
            close()
        }
        drawPath(body, shell)
        drawPath(body, edge, style = Stroke(8f * sx))
        // Front lip adds a little depth instead of a flat icon look.
        val lip = Path().apply {
            moveTo(p(153f, 486f).x, p(153f, 486f).y)
            cubicTo(p(245f, 548f).x, p(245f, 548f).y, p(300f, 510f).x, p(300f, 510f).y, p(365f, 454f).x, p(365f, 454f).y)
            cubicTo(p(430f, 408f).x, p(430f, 408f).y, p(570f, 408f).x, p(570f, 408f).y, p(635f, 454f).x, p(635f, 454f).y)
            cubicTo(p(700f, 510f).x, p(700f, 510f).y, p(755f, 548f).x, p(755f, 548f).y, p(847f, 486f).x, p(847f, 486f).y)
        }
        drawPath(lip, Color(0xFF9AA2A4), style = Stroke(13f * sx))

        // Shoulder buttons sit below the visible trigger paddles.
        rect(245f, 164f, 170f, 45f, mid, 17f)
        rect(585f, 164f, 170f, 45f, mid, 17f)
        rect(255f, 169f, 150f, 25f, Color(0xFF899397), 12f)
        rect(595f, 169f, 150f, 25f, Color(0xFF899397), 12f)

        // Analog stick wells and live-moving caps.
        circle(320f, 325f, 78f, Color(0xFFBFC5C5), edge, 9f)
        circle(690f, 382f, 78f, Color(0xFFBFC5C5), edge, 9f)
        circle(320f + leftX.coerceIn(-1f, 1f) * 35f, 325f + leftY.coerceIn(-1f, 1f) * 35f, 49f, mid, dark, 10f)
        circle(690f + rightX.coerceIn(-1f, 1f) * 35f, 382f + rightY.coerceIn(-1f, 1f) * 35f, 49f, mid, dark, 10f)
        circle(320f + leftX.coerceIn(-1f, 1f) * 35f, 325f + leftY.coerceIn(-1f, 1f) * 35f, 9f, lime, lime, 2f)
        circle(690f + rightX.coerceIn(-1f, 1f) * 35f, 382f + rightY.coerceIn(-1f, 1f) * 35f, 9f, lime, lime, 2f)

        // D-pad.
        circle(470f, 350f, 72f, Color(0xFFBFC5C5), edge, 8f)
        rect(451f, 292f, 38f, 116f, dark, 8f)
        rect(412f, 331f, 116f, 38f, dark, 8f)
        circle(470f, 350f, 17f, mid, dark, 4f)

        // Center buttons and Xbox jewel.
        circle(505f, 280f, 26f, Color(0xFFCAD0CE), edge, 5f)
        circle(575f, 280f, 26f, Color(0xFFCAD0CE), edge, 5f)
        circle(540f, 235f, 33f, Color(0xFFCBD6C5), lime, 5f)
        val logo = Path().apply {
            moveTo(p(519f, 213f).x, p(519f, 213f).y)
            lineTo(p(540f, 224f).x, p(540f, 224f).y)
            lineTo(p(561f, 213f).x, p(561f, 213f).y)
            lineTo(p(547f, 236f).x, p(547f, 236f).y)
            lineTo(p(568f, 257f).x, p(568f, 257f).y)
            lineTo(p(540f, 244f).x, p(540f, 244f).y)
            lineTo(p(512f, 257f).x, p(512f, 257f).y)
            lineTo(p(533f, 236f).x, p(533f, 236f).y)
            close()
        }
        drawPath(logo, Color(0xFF607A3A))

        // ABXY cluster.
        circle(790f, 278f, 35f, Color(0xFFE2B23C))
        circle(845f, 342f, 35f, Color(0xFFC94A3B))
        circle(785f, 405f, 35f, Color(0xFF789B4A))
        circle(730f, 342f, 35f, Color(0xFF5577C5))

        // Focus rings also show the currently mapped shoulder/trigger.
        focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.L3), 320f, 325f, 62f)
        focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.R3), 690f, 382f, 62f)
        focus(setOf(XboxInput.DPAD_UP, XboxInput.DPAD_DOWN, XboxInput.DPAD_LEFT, XboxInput.DPAD_RIGHT), 470f, 350f, 78f)
        focus(setOf(XboxInput.Y), 790f, 278f, 42f)
        focus(setOf(XboxInput.B), 845f, 342f, 42f)
        focus(setOf(XboxInput.A), 785f, 405f, 42f)
        focus(setOf(XboxInput.X), 730f, 342f, 42f)
        focus(setOf(XboxInput.L1), 330f, 185f, 48f)
        focus(setOf(XboxInput.R1), 670f, 185f, 48f)
        focus(setOf(XboxInput.L2), 315f, 122f, 72f)
        focus(setOf(XboxInput.R2), 685f, 122f, 72f)
        focus(setOf(XboxInput.BACK), 505f, 280f, 32f)
        focus(setOf(XboxInput.START), 575f, 280f, 32f)
    }
}
