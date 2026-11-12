package com.gamepadbridge.preview
// Preview build: AndroidX enabled in root gradle.properties.
import android.app.Activity
import android.os.Bundle
import android.view.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

enum class Btn { A,B,X,Y,LB,RB,LT,RT,L3,R3,START,BACK,UP,DOWN,LEFT,RIGHT }
data class State(val keys:Set<Btn> = emptySet(),val lx:Float=0f,val ly:Float=0f,val rx:Float=0f,val ry:Float=0f,val lt:Float=0f,val rt:Float=0f,val device:String="")
class MainActivity:Activity(){
 private val live=MutableStateFlow(State())
 private var page by mutableIntStateOf(0)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8FC43A))){when(page){0->Home(live.value.device,{page=1},{page=2});1->Test(live.value){page=0};else->Mapping(live.value){page=0}}}}}
 override fun dispatchKeyEvent(e:KeyEvent):Boolean{
 val b=when(e.keyCode){KeyEvent.KEYCODE_BUTTON_A->Btn.A;KeyEvent.KEYCODE_BUTTON_B->Btn.B;KeyEvent.KEYCODE_BUTTON_X->Btn.X;KeyEvent.KEYCODE_BUTTON_Y->Btn.Y;KeyEvent.KEYCODE_BUTTON_L1->Btn.LB;KeyEvent.KEYCODE_BUTTON_R1->Btn.RB;KeyEvent.KEYCODE_BUTTON_L2->Btn.LT;KeyEvent.KEYCODE_BUTTON_R2->Btn.RT;KeyEvent.KEYCODE_BUTTON_THUMBL->Btn.L3;KeyEvent.KEYCODE_BUTTON_THUMBR->Btn.R3;KeyEvent.KEYCODE_BUTTON_START->Btn.START;KeyEvent.KEYCODE_BUTTON_SELECT->Btn.BACK;KeyEvent.KEYCODE_DPAD_UP->Btn.UP;KeyEvent.KEYCODE_DPAD_DOWN->Btn.DOWN;KeyEvent.KEYCODE_DPAD_LEFT->Btn.LEFT;KeyEvent.KEYCODE_DPAD_RIGHT->Btn.RIGHT;else->null}
 if(b!=null){val s=live.value;live.value=s.copy(keys=if(e.action==KeyEvent.ACTION_DOWN)s.keys+b else s.keys-b,device=e.device?.name?:"Gamepad");return true};return super.dispatchKeyEvent(e)}
 override fun dispatchGenericMotionEvent(e:MotionEvent):Boolean{if(e.action==MotionEvent.ACTION_MOVE){val s=live.value;fun a(x:Int,y:Int=x)=e.getAxisValue(x).let{if(abs(it)>.001f)it else e.getAxisValue(y)}.coerceIn(-1f,1f);live.value=s.copy(lx=a(MotionEvent.AXIS_X),ly=a(MotionEvent.AXIS_Y),rx=a(MotionEvent.AXIS_Z,MotionEvent.AXIS_RX),ry=a(MotionEvent.AXIS_RZ,MotionEvent.AXIS_RY),lt=e.getAxisValue(MotionEvent.AXIS_LTRIGGER).coerceIn(0f,1f),rt=e.getAxisValue(MotionEvent.AXIS_RTRIGGER).coerceIn(0f,1f),device=e.device?.name?:"Gamepad");return true};return super.dispatchGenericMotionEvent(e)}
}
val green=Color(0xFF8FC43A);val bg=Color(0xFF09120C);val panel=Color(0xFF1B2A20);val muted=Color(0xFFB9C4AE)
@Composable fun Shell(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF13251A),bg))).verticalScroll(rememberScrollState()).padding(20.dp),content=content)}
@Composable fun Head(){Text("GAMEPAD BRIDGE",fontSize=23.sp,color=Color.White,fontWeight=FontWeight.Black);Text("DASHBOARD XBOX • PRÉVIA",fontSize=10.sp,color=green)}
@Composable fun Tile(t:String,click:()->Unit,hot:Boolean=false){Box(Modifier.fillMaxWidth().padding(vertical=6.dp).background(if(hot)green else panel,RoundedCornerShape(12.dp)).clickable{click()}.padding(18.dp),contentAlignment=Alignment.Center){Text(t,color=if(hot)Color.Black else Color.White,fontWeight=FontWeight.Bold)}}
@Composable fun Home(device:String,test:()->Unit,map:()->Unit){Shell{Head();Spacer(Modifier.height(35.dp));Text("SEU CONTROLE.",fontSize=28.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("SEU JEITO DE JOGAR.",fontSize=23.sp,color=green,fontWeight=FontWeight.Bold);Text(if(device.isEmpty())"Conecte seu controle" else "CONTROLE: "+device,color=muted);Spacer(Modifier.height(20.dp));Image(painterResource(R.drawable.controller_xbox360),null,Modifier.fillMaxWidth().height(200.dp));Tile("TESTAR CONTROLE",test,true);Tile("MAPEAMENTO VISUAL",map);Text("Botões • analógicos • gatilhos em tempo real",color=muted)}}
@Composable fun Test(s:State,back:()->Unit){Shell{Text("‹ VOLTAR",color=green,modifier=Modifier.clickable{back()});Text("TESTE DE CONTROLE",fontSize=25.sp,color=Color.White,fontWeight=FontWeight.Bold);Text(if(s.device.isEmpty())"Aguardando entrada..." else s.device,color=muted);Image(painterResource(R.drawable.controller_xbox360),null,Modifier.fillMaxWidth().height(205.dp));Text("BOTÕES: "+s.keys.joinToString(),color=green);Text("ANALÓGICOS",color=Color.White,fontWeight=FontWeight.Bold);Meter("ESQUERDO X",s.lx);Meter("ESQUERDO Y",s.ly);Meter("DIREITO X",s.rx);Meter("DIREITO Y",s.ry);Text("GATILHOS",color=Color.White,fontWeight=FontWeight.Bold);Meter("LT",s.lt,true);Meter("RT",s.rt,true)}}
@Composable fun Meter(n:String,v:Float,u:Boolean=false){Column(Modifier.padding(vertical=5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(n,color=muted);Text((v*100).toInt().toString()+"%",color=Color.White)};LinearProgressIndicator(progress={(if(u)v else (v+1f)/2f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=green)}}
@Composable fun Mapping(s:State,back:()->Unit){Shell{Head();Text("MAPEAMENTO DO CONTROLE",fontSize=22.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("Tela de prévia do mapeamento",color=muted);Image(painterResource(R.drawable.controller_xbox360),null,Modifier.fillMaxWidth().height(220.dp));Text("Entradas recebidas: "+s.keys.joinToString(),color=green);Tile("VOLTAR",back,true)}}
