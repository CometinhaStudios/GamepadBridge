package com.gamepadbridge.preview
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class GamepadPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var testing = false
    private val pressed = mutableSetOf<String>()
    private var lx=0f; private var ly=0f; private var rx=0f; private var ry=0f
    private var lt=0f; private var rt=0f
    private var deviceName=""
    private var receivedInput=false
    private val green=Color.rgb(154,216,45)
    private val bg=Color.rgb(11,16,23)
    private val panel=Color.rgb(23,31,42)
    private val muted=Color.rgb(137,149,160)
    private val white=Color.rgb(239,243,245)
    init { isFocusable=true; isFocusableInTouchMode=true }

    fun showHome(): Boolean { if(testing){testing=false;invalidate();return true};return false }
    fun handleKey(e:KeyEvent):Boolean {
        if(e.source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
           e.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val n=keyName(e.keyCode)?:return false
        deviceName=e.device?.name?: "Gamepad conectado"; receivedInput=true
        if(e.action==KeyEvent.ACTION_DOWN) pressed.add(n) else if(e.action==KeyEvent.ACTION_UP) pressed.remove(n)
        invalidate(); return true
    }
    fun handleMotion(e:MotionEvent):Boolean {
        if(e.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK &&
           e.source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD) return false
        deviceName=e.device?.name?: "Gamepad conectado"; receivedInput=true
        lx=e.getAxisValue(MotionEvent.AXIS_X).coerceIn(-1f,1f)
        ly=e.getAxisValue(MotionEvent.AXIS_Y).coerceIn(-1f,1f)
        rx=(e.getAxisValue(MotionEvent.AXIS_Z).takeIf{abs(it)>0.001f}?:e.getAxisValue(MotionEvent.AXIS_RX)).coerceIn(-1f,1f)
        ry=(e.getAxisValue(MotionEvent.AXIS_RZ).takeIf{abs(it)>0.001f}?:e.getAxisValue(MotionEvent.AXIS_RY)).coerceIn(-1f,1f)
        lt=trigger(e,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_BRAKE)
        rt=trigger(e,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_GAS)
        direction("UP",e.getAxisValue(MotionEvent.AXIS_HAT_Y)<-0.5f)
        direction("DOWN",e.getAxisValue(MotionEvent.AXIS_HAT_Y)>0.5f)
        direction("LEFT",e.getAxisValue(MotionEvent.AXIS_HAT_X)<-0.5f)
        direction("RIGHT",e.getAxisValue(MotionEvent.AXIS_HAT_X)>0.5f)
        invalidate();return true
    }
    private fun trigger(e:MotionEvent,a:Int,b:Int):Float { val x=e.getAxisValue(a);return (if(x>0.001f)x else e.getAxisValue(b)).coerceIn(0f,1f) }
    private fun direction(n:String,on:Boolean){if(on)pressed.add(n)else pressed.remove(n)}
    private fun keyName(k:Int):String?=when(k){
        KeyEvent.KEYCODE_BUTTON_A->"A";KeyEvent.KEYCODE_BUTTON_B->"B";KeyEvent.KEYCODE_BUTTON_X->"X";KeyEvent.KEYCODE_BUTTON_Y->"Y"
        KeyEvent.KEYCODE_BUTTON_L1->"LB";KeyEvent.KEYCODE_BUTTON_R1->"RB";KeyEvent.KEYCODE_BUTTON_L2->"LT";KeyEvent.KEYCODE_BUTTON_R2->"RT"
        KeyEvent.KEYCODE_BUTTON_THUMBL->"L3";KeyEvent.KEYCODE_BUTTON_THUMBR->"R3"
        KeyEvent.KEYCODE_BUTTON_START->"START";KeyEvent.KEYCODE_BUTTON_SELECT->"BACK";KeyEvent.KEYCODE_BUTTON_MODE->"GUIDE"
        KeyEvent.KEYCODE_DPAD_UP->"UP";KeyEvent.KEYCODE_DPAD_DOWN->"DOWN";KeyEvent.KEYCODE_DPAD_LEFT->"LEFT";KeyEvent.KEYCODE_DPAD_RIGHT->"RIGHT"
        else->null
    }

    override fun onDraw(c:Canvas){
        super.onDraw(c)
        val s=min(width/393f,height/820f)
        c.save();c.translate((width-393f*s)/2f,(height-820f*s)/2f);c.scale(s,s);c.drawColor(bg)
        header(c);if(testing)testScreen(c)else home(c);c.restore()
    }
    private fun header(c:Canvas){
        txt(c,"GAMEPAD",24f,42f,13f,green,true,spacing=2f);txt(c,"BRIDGE",24f,61f,13f,white,true,spacing=2f)
        box(c,294f,24f,75f,31f,15f,Color.rgb(31,43,52));dot(c,307f,39f,4f,if(receivedInput)green else muted)
        txt(c,if(receivedInput)"INPUT OK" else "PREVIEW",318f,43f,9f,white,true);ln(c,24f,82f,369f,82f,Color.rgb(40,50,60),1f)
    }
    private fun home(c:Canvas){
        txt(c,"SEU CONTROLE.",24f,139f,27f,white,true);txt(c,"SEU JEITO DE JOGAR.",24f,171f,20f,green,true)
        txt(c,"Central de teste e diagnóstico de gamepad",24f,198f,11f,muted)
        box(c,24f,226f,345f,337f,22f,panel);txt(c,"XBOX 360 STYLE",45f,258f,10f,green,true,spacing=1.5f)
        controller(c,40f,275f,0.78f)
        box(c,24f,585f,345f,76f,16f,Color.rgb(28,39,49));dot(c,48f,610f,5f,if(receivedInput)green else muted)
        txt(c,if(receivedInput)"CONTROLE DETECTADO" else "AGUARDANDO CONTROLE",63f,614f,11f,white,true)
        txt(c,if(receivedInput)deviceName.take(34) else "Conecte um gamepad para começar",63f,635f,10f,muted)
        box(c,24f,682f,345f,58f,17f,green);txt(c,"ABRIR TESTE DE GAMEPAD",196f,717f,13f,bg,true,center=true)
        txt(c,"PRÉVIA VISUAL • ENTRADAS REAIS QUANDO DISPONÍVEIS",196f,777f,7.5f,muted,center=true)
    }
    private fun testScreen(c:Canvas){
        txt(c,"TESTE DE",24f,132f,24f,white,true);txt(c,"CONTROLE",24f,160f,24f,green,true)
        txt(c,if(receivedInput)deviceName.take(40) else "Nenhuma entrada recebida ainda",24f,185f,10f,muted)
        box(c,24f,205f,345f,363f,20f,panel);txt(c,"MAPA DE BOTÕES",43f,232f,10f,muted,true,spacing=1.2f)
        controller(c,34f,251f,0.84f)
        box(c,24f,586f,164f,79f,14f,panel);box(c,205f,586f,164f,79f,14f,panel)
        txt(c,"ANALÓGICO ESQ.",39f,608f,9f,muted,true);txt(c,"X "+fmt(lx)+"   Y "+fmt(ly),39f,635f,11f,white,true)
        txt(c,"ANALÓGICO DIR.",220f,608f,9f,muted,true);txt(c,"X "+fmt(rx)+"   Y "+fmt(ry),220f,635f,11f,white,true)
        box(c,24f,681f,345f,70f,14f,panel);txt(c,"GATILHOS",42f,704f,9f,muted,true)
        txt(c,"LT  "+(lt*100).toInt()+"%",42f,731f,11f,white,true);txt(c,"RT  "+(rt*100).toInt()+"%",206f,731f,11f,white,true)
        box(c,42f,739f,128f,4f,2f,Color.rgb(52,64,72));box(c,42f,739f,128f*lt,4f,2f,green)
        box(c,206f,739f,128f,4f,2f,Color.rgb(52,64,72));box(c,206f,739f,128f*rt,4f,2f,green)
        box(c,24f,770f,345f,36f,12f,Color.rgb(32,45,55));txt(c,"VOLTAR",196f,793f,10f,white,true,center=true)
    }
    private fun fmt(v:Float)=String.format(java.util.Locale.US,"%+.2f",v)
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.action==MotionEvent.ACTION_UP){
            val s=min(width/393f,height/820f);val x=(e.x-(width-393f*s)/2f)/s;val y=(e.y-(height-820f*s)/2f)/s
            if(!testing&&y in 670f..750f&&x in 15f..378f)testing=true else if(testing&&y>760f)testing=false
            invalidate()
        };return true
    }
    private fun controller(c:Canvas,ox:Float,oy:Float,s:Float){
        c.save();c.translate(ox,oy);c.scale(s,s)
        val b=Path().apply{
            moveTo(55f,15f);cubicTo(72f,-2f,117f,-2f,137f,10f);cubicTo(151f,20f,159f,29f,196f,29f)
            cubicTo(233f,29f,241f,20f,255f,10f);cubicTo(275f,-2f,320f,-2f,337f,15f)
            cubicTo(363f,50f,375f,151f,365f,207f);cubicTo(358f,246f,331f,249f,304f,223f)
            lineTo(265f,183f);cubicTo(250f,167f,228f,160f,196f,160f);cubicTo(164f,160f,142f,167f,127f,183f)
            lineTo(88f,223f);cubicTo(61f,249f,34f,246f,27f,207f);cubicTo(17f,151f,29f,50f,55f,15f);close()
        }
        p.color=Color.rgb(96,106,112);p.style=Paint.Style.FILL;c.drawPath(b,p)
        p.color=Color.rgb(232,236,235);c.save();c.translate(0f,-3f);c.drawPath(b,p);c.restore()
        circle(c,196f,65f,24f,if(pressed.contains("GUIDE"))green else Color.rgb(205,214,211),Color.rgb(133,153,139),2f)
        txt(c,"X",196f,73f,27f,Color.rgb(104,143,58),true,center=true)
        stick(c,91f,68f,lx,ly,"L3")
        stick(c,301f,143f,rx,ry,"R3")
        circle(c,145f,143f,35f,Color.rgb(65,77,85),Color.rgb(35,43,49),2f)
        listOf(Triple("UP",145f,121f),Triple("DOWN",145f,165f),Triple("LEFT",123f,143f),Triple("RIGHT",167f,143f)).forEach{(n,x,y)->circle(c,x,y,12f,if(pressed.contains(n))green else Color.rgb(85,98,105),Color.rgb(40,49,55),1f)}
        face(c,330f,49f,"Y",Color.rgb(239,177,47));face(c,356f,78f,"B",Color.rgb(211,62,44))
        face(c,304f,91f,"A",Color.rgb(105,157,73));face(c,278f,63f,"X",Color.rgb(72,112,202))
        shoulder(c,65f,24f,"LB");shoulder(c,281f,24f,"RB")
        c.restore()
    }
    private fun stick(c:Canvas,x:Float,y:Float,ax:Float,ay:Float,n:String){
        circle(c,x,y,28f,Color.rgb(63,74,82),Color.rgb(35,43,49),3f)
        val px=x+(ax.coerceIn(-1f,1f)*13f);val py=y+(ay.coerceIn(-1f,1f)*13f)
        circle(c,px,py,15f,if(pressed.contains(n))green else Color.rgb(84,98,107),Color.rgb(39,49,56),2f)
    }
    private fun face(c:Canvas,x:Float,y:Float,n:String,color:Int){circle(c,x,y,11f,if(pressed.contains(n))green else color,Color.rgb(56,61,64),1.4f);txt(c,n,x,y+3.5f,8f,Color.WHITE,true,center=true)}
    private fun shoulder(c:Canvas,x:Float,y:Float,n:String){box(c,x,y,31f,13f,6f,if(pressed.contains(n))green else Color.rgb(72,83,90));txt(c,n,x+15.5f,y+9.5f,7f,white,true,center=true)}
    private fun circle(c:Canvas,x:Float,y:Float,r:Float,fill:Int,stroke:Int,sw:Float){p.style=Paint.Style.FILL;p.color=fill;c.drawCircle(x,y,r,p);p.style=Paint.Style.STROKE;p.strokeWidth=sw;p.color=stroke;c.drawCircle(x,y,r,p);p.style=Paint.Style.FILL}
    private fun box(c:Canvas,x:Float,y:Float,w:Float,h:Float,r:Float,color:Int){p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(RectF(x,y,x+w,y+h),r,r,p)}
    private fun txt(c:Canvas,t:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,center:Boolean=false,spacing:Float=0f){p.style=Paint.Style.FILL;p.color=color;p.textSize=size;p.typeface=if(bold)android.graphics.Typeface.create("sans-serif",1)else android.graphics.Typeface.create("sans-serif",0);p.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT;c.drawText(t,x,y,p)}
    private fun ln(c:Canvas,x:Float,y:Float,x2:Float,y2:Float,color:Int,sw:Float){p.color=color;p.strokeWidth=sw;p.style=Paint.Style.STROKE;c.drawLine(x,y,x2,y2,p);p.style=Paint.Style.FILL}
    private fun dot(c:Canvas,x:Float,y:Float,r:Float,color:Int){p.color=color;p.style=Paint.Style.FILL;c.drawCircle(x,y,r,p)}
}
