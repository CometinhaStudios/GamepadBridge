package com.gamepadbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.gamepadbridge.app.data.XboxInput

@Composable
fun XboxControllerVisual(
    highlight: XboxInput?,
    modifier: Modifier = Modifier,
    pressed: Set<XboxInput> = emptySet(),
    leftX: Float = 0f, leftY: Float = 0f, rightX: Float = 0f, rightY: Float = 0f
) {
    Canvas(modifier.fillMaxWidth().aspectRatio(1.55f)) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w*x,h*y)
        val shell = Path().apply {
            moveTo(w*.20f,h*.22f)
            cubicTo(w*.13f,h*.22f,w*.10f,h*.39f,w*.10f,h*.62f)
            cubicTo(w*.09f,h*.84f,w*.16f,h*.96f,w*.27f,h*.89f)
            cubicTo(w*.37f,h*.80f,w*.40f,h*.76f,w*.50f,h*.76f)
            cubicTo(w*.60f,h*.76f,w*.64f,h*.80f,w*.73f,h*.89f)
            cubicTo(w*.84f,h*.96f,w*.91f,h*.84f,w*.90f,h*.62f)
            cubicTo(w*.90f,h*.39f,w*.87f,h*.22f,w*.80f,h*.22f)
            cubicTo(w*.70f,h*.18f,w*.66f,h*.24f,w*.50f,h*.24f)
            cubicTo(w*.34f,h*.24f,w*.30f,h*.18f,w*.20f,h*.22f)
            close()
        }
        drawPath(shell,Color(0xFF101522))
        drawPath(shell,Brush.verticalGradient(listOf(Color(0xFFF9FAFF),Color(0xFFD8DCE8),Color(0xFFADB4C4))))
        drawPath(shell,Color(0xFF737D91),style=Stroke(w*.008f))

        fun ring(inputs:Set<XboxInput>,x:Float,y:Float,r:Float) {
            if (inputs.any { it==highlight || it in pressed }) {
                drawCircle(Color(0x5539A7FF),r*1.7f,p(x,y))
                drawCircle(Color(0xFF168DFF),r*1.35f,p(x,y),style=Stroke(w*.012f))
            }
        }
        fun button(x:Float,y:Float,label:String,color:Color) {
            drawCircle(Color(0xFF626B7B),w*.036f,p(x,y+.008f))
            drawCircle(color,w*.032f,p(x,y))
            drawCircle(Color.White.copy(alpha=.35f),w*.026f,p(x-.004f,y-.005f),style=Stroke(w*.004f))
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color=android.graphics.Color.WHITE
                textAlign=android.graphics.Paint.Align.CENTER
                textSize=w*.035f
                typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText(label,w*x,h*y+w*.012f,paint)
        }
        fun stick(x:Float,y:Float,dx:Float=0f,dy:Float=0f) {
            drawCircle(Color(0xFF8C94A4),w*.075f,p(x,y))
            drawCircle(Color(0xFF343B49),w*.061f,p(x,y))
            drawCircle(Color(0xFF171D29),w*.049f,p(x+dx*.025f,y+dy*.025f))
            drawCircle(Color(0xFF5C6575),w*.038f,p(x+dx*.025f,y+dy*.025f),style=Stroke(w*.004f))
        }

        drawRoundRect(Color(0xFF7D8798),p(.19f,.17f),Size(w*.19f,h*.075f),CornerRadius(w*.035f))
        drawRoundRect(Color(0xFF7D8798),p(.62f,.17f),Size(w*.19f,h*.075f),CornerRadius(w*.035f))
        drawRoundRect(Color(0xFF303746),p(.21f,.205f),Size(w*.16f,h*.045f),CornerRadius(w*.025f))
        drawRoundRect(Color(0xFF303746),p(.63f,.205f),Size(w*.16f,h*.045f),CornerRadius(w*.025f))
        ring(setOf(XboxInput.L2),.28f,.18f,w*.045f)
        ring(setOf(XboxInput.R2),.72f,.18f,w*.045f)
        ring(setOf(XboxInput.L1),.28f,.23f,w*.045f)
        ring(setOf(XboxInput.R1),.72f,.23f,w*.045f)

        stick(.30f,.48f,leftX,leftY)
        stick(.68f,.65f,rightX,rightY)
        ring(setOf(XboxInput.LS_X,XboxInput.LS_Y,XboxInput.L3),.30f,.48f,w*.073f)
        ring(setOf(XboxInput.RS_X,XboxInput.RS_Y,XboxInput.R3),.68f,.65f,w*.073f)
        drawCircle(Color(0xFF7C8595),w*.072f,p(.43f,.68f))
        drawRoundRect(Color(0xFF242B38),p(.414f,.625f),Size(w*.032f,h*.11f),CornerRadius(w*.009f))
        drawRoundRect(Color(0xFF242B38),p(.398f,.657f),Size(w*.064f,h*.045f),CornerRadius(w*.009f))
        ring(setOf(XboxInput.DPAD_UP,XboxInput.DPAD_DOWN,XboxInput.DPAD_LEFT,XboxInput.DPAD_RIGHT),.43f,.68f,w*.075f)

        button(.78f,.43f,"Y",Color(0xFFE5A900))
        button(.83f,.55f,"B",Color(0xFFD83232))
        button(.75f,.66f,"A",Color(0xFF42A83D))
        button(.70f,.54f,"X",Color(0xFF2585D8))
        ring(setOf(XboxInput.Y),.78f,.43f,w*.034f)
        ring(setOf(XboxInput.B),.83f,.55f,w*.034f)
        ring(setOf(XboxInput.A),.75f,.66f,w*.034f)
        ring(setOf(XboxInput.X),.70f,.54f,w*.034f)

        drawCircle(Color(0xFF6D7788),w*.052f,p(.52f,.45f))
        drawCircle(Color(0xFF1C2635),w*.044f,p(.52f,.45f))
        drawCircle(Color(0xFF48A6F5),w*.025f,p(.52f,.45f),style=Stroke(w*.009f))
        drawCircle(Color(0xFF596273),w*.018f,p(.42f,.47f))
        drawCircle(Color(0xFF596273),w*.018f,p(.62f,.47f))
        ring(setOf(XboxInput.BACK),.42f,.47f,w*.022f)
        ring(setOf(XboxInput.START),.62f,.47f,w*.022f)

        if (leftX!=0f || leftY!=0f) drawCircle(Color(0xFF39A7FF),w*.012f,p(.30f+leftX*.025f,.48f+leftY*.025f))
        if (rightX!=0f || rightY!=0f) drawCircle(Color(0xFF39A7FF),w*.012f,p(.68f+rightX*.025f,.65f+rightY*.025f))
    }
}
