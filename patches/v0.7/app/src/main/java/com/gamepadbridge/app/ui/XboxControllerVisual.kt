package com.gamepadbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.gamepadbridge.app.data.XboxInput

/**
 * Front-view Gamepad Bridge controller illustration, traced to the supplied controller artwork.
 * Coordinates use the original 1536 x 1084 artwork canvas so button hit/highlight locations stay aligned.
 */
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
    Canvas(modifier.fillMaxWidth().aspectRatio(1536f / 1084f)) {
        val scale = size.width / 1536f
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            val shell = Color(0xFFECEEEE)
            val outline = Color(0xFF858B8D)
            val graphite = Color(0xFF4E595F)
            val graphiteDark = Color(0xFF303A3F)
            val yellow = Color(0xFFE6AA36)
            val red = Color(0xFFC83220)
            val blue = Color(0xFF526FC6)
            val green = Color(0xFF7F963F)
            val lime = Color(0xFFC7EF35)
            val grey = Color(0xFFB9BDBD)

            val body = Path().apply {
                moveTo(213f, 72f)
                cubicTo(265f, 25f, 365f, -5f, 430f, 12f)
                cubicTo(458f, 43f, 467f, 49f, 510f, 61f)
                cubicTo(605f, 91f, 690f, 109f, 770f, 102f)
                cubicTo(865f, 96f, 946f, 64f, 1010f, 35f)
                cubicTo(1060f, -2f, 1150f, 17f, 1238f, 77f)
                cubicTo(1305f, 168f, 1367f, 394f, 1408f, 620f)
                cubicTo(1443f, 810f, 1435f, 960f, 1372f, 1002f)
                cubicTo(1318f, 1038f, 1270f, 1003f, 1230f, 963f)
                cubicTo(1110f, 840f, 1065f, 754f, 970f, 722f)
                cubicTo(875f, 689f, 680f, 690f, 580f, 710f)
                cubicTo(475f, 730f, 430f, 802f, 300f, 934f)
                cubicTo(236f, 1000f, 190f, 1025f, 120f, 1002f)
                cubicTo(42f, 978f, 20f, 865f, 30f, 700f)
                cubicTo(48f, 480f, 105f, 220f, 160f, 120f)
                close()
            }
            drawPath(body, shell)
            drawPath(body, outline, style = Stroke(7f))

            val lowerRim = Path().apply {
                moveTo(157f, 1005f)
                cubicTo(275f, 943f, 367f, 811f, 455f, 759f)
                cubicTo(550f, 700f, 660f, 690f, 770f, 695f)
                cubicTo(930f, 694f, 1010f, 722f, 1085f, 797f)
                cubicTo(1160f, 873f, 1225f, 970f, 1298f, 1005f)
            }
            drawPath(lowerRim, graphiteDark, style = Stroke(20f))
            drawPath(lowerRim, outline, style = Stroke(8f))

            fun circle(x: Float, y: Float, r: Float, fill: Color, border: Color = graphiteDark, sw: Float = 8f) {
                drawCircle(fill, r, Offset(x, y))
                drawCircle(border, r, Offset(x, y), style = Stroke(sw))
            }

            // Left and right thumbsticks
            circle(326f, 217f, 96f, graphite, graphiteDark, 10f)
            circle(934f, 444f, 99f, graphite, graphiteDark, 10f)
            // D-pad disc and four recessed direction seams
            circle(523f, 464f, 120f, graphite, graphiteDark, 9f)
            val dpadLines = Path().apply {
                moveTo(495f, 350f); cubicTo(490f, 405f, 481f, 421f, 412f, 429f)
                moveTo(558f, 350f); cubicTo(560f, 405f, 568f, 421f, 635f, 429f)
                moveTo(412f, 499f); cubicTo(482f, 505f, 490f, 526f, 495f, 578f)
                moveTo(635f, 499f); cubicTo(568f, 505f, 560f, 526f, 558f, 578f)
            }
            drawPath(dpadLines, graphiteDark, style = Stroke(9f))

            // Xbox / menu cluster
            circle(733f, 247f, 77f, Color(0xFFC9D5C9), lime, 7f)
            val xbox = Path().apply {
                moveTo(685f, 193f); lineTo(733f, 214f); lineTo(781f, 193f)
                lineTo(747f, 235f); lineTo(797f, 291f); lineTo(733f, 251f)
                lineTo(669f, 291f); lineTo(719f, 235f); close()
            }
            drawPath(xbox, green)

            // Back and Start buttons
            circle(589f, 247f, 39f, Color(0xFFD9DDDC), grey, 6f)
            circle(880f, 247f, 39f, Color(0xFFD9DDDC), grey, 6f)
            val backTri = Path().apply { moveTo(570f,247f); lineTo(600f,229f); lineTo(600f,265f); close() }
            val startTri = Path().apply { moveTo(898f,247f); lineTo(868f,229f); lineTo(868f,265f); close() }
            drawPath(backTri, graphite, style = Stroke(7f))
            drawPath(startTri, graphite, style = Stroke(7f))

            // Coloured ABXY face buttons
            circle(1128f, 139f, 51f, yellow, Color(0xFF9B7137), 8f)
            circle(1240f, 242f, 49f, red, Color(0xFF874A36), 8f)
            circle(1135f, 342f, 51f, green, Color(0xFF485C39), 8f)
            circle(1027f, 240f, 50f, blue, Color(0xFF263C79), 8f)

            // Exact-colour focus ring and soft fill for the step currently being mapped.
            fun focus(inputs: Set<XboxInput>, x: Float, y: Float, r: Float, color: Color) {
                if (highlight in inputs || inputs.any { it in pressed }) {
                    drawCircle(color.copy(alpha = .30f), r * 1.55f, Offset(x, y))
                    drawCircle(color.copy(alpha = .16f), r * 1.20f, Offset(x, y))
                    drawCircle(color, r, Offset(x, y), style = Stroke(8f))
                }
            }
            focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.L3), 326f, 217f, 108f, graphite)
            focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.R3), 934f, 444f, 110f, graphite)
            focus(setOf(XboxInput.DPAD_UP, XboxInput.DPAD_DOWN, XboxInput.DPAD_LEFT, XboxInput.DPAD_RIGHT), 523f, 464f, 128f, graphite)
            focus(setOf(XboxInput.Y), 1128f, 139f, 59f, yellow)
            focus(setOf(XboxInput.B), 1240f, 242f, 57f, red)
            focus(setOf(XboxInput.A), 1135f, 342f, 59f, green)
            focus(setOf(XboxInput.X), 1027f, 240f, 58f, blue)
            focus(setOf(XboxInput.BACK), 589f, 247f, 45f, grey)
            focus(setOf(XboxInput.START), 880f, 247f, 45f, grey)
            focus(setOf(XboxInput.L1, XboxInput.L2), 245f, 105f, 48f, grey)
            focus(setOf(XboxInput.R1, XboxInput.R2), 1280f, 105f, 48f, grey)

            if (leftX != 0f || leftY != 0f) drawCircle(lime, 12f, Offset(326f + leftX * 50f, 217f + leftY * 50f))
            if (rightX != 0f || rightY != 0f) drawCircle(lime, 12f, Offset(934f + rightX * 50f, 444f + rightY * 50f))
        }
    }
}
