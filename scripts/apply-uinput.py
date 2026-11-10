from pathlib import Path
import re

root = Path("app/src/main")
java = root / "java/com/gamepadbridge/app"
aidl = root / "aidl/com/gamepadbridge/app/ipc"
cpp = root / "cpp"
for d in (java / "inject", aidl, cpp):
    d.mkdir(parents=True, exist_ok=True)

(aidl / "IGamepadUInput.aidl").write_text("""package com.gamepadbridge.app.ipc;
interface IGamepadUInput {
    boolean canOpen();
    boolean create();
    void sendKey(int androidKeyCode, boolean down);
    void sendAxis(int androidAxis, float value);
    void destroy();
}
""")

(java / "inject/UInputNative.kt").write_text("""package com.gamepadbridge.app.inject
object UInputNative {
    init { System.loadLibrary("gamepad_uinput") }
    external fun canOpen(): Boolean
    external fun create(): Boolean
    external fun sendKey(androidKeyCode: Int, down: Boolean)
    external fun sendAxis(androidAxis: Int, value: Float)
    external fun destroy()
}
""")

(java / "inject/GamepadUInputService.kt").write_text("""package com.gamepadbridge.app.inject
import android.util.Log
import com.gamepadbridge.app.ipc.IGamepadUInput

class GamepadUInputService : IGamepadUInput.Stub() {
    override fun canOpen(): Boolean = try { UInputNative.canOpen() } catch (t: Throwable) {
        Log.e("GamepadUInput", "open /dev/uinput failed", t); false
    }
    override fun create(): Boolean = try { UInputNative.create() } catch (t: Throwable) {
        Log.e("GamepadUInput", "create virtual pad failed", t); false
    }
    override fun sendKey(androidKeyCode: Int, down: Boolean) {
        try { UInputNative.sendKey(androidKeyCode, down) } catch (t: Throwable) { Log.e("GamepadUInput", "sendKey", t) }
    }
    override fun sendAxis(androidAxis: Int, value: Float) {
        try { UInputNative.sendAxis(androidAxis, value) } catch (t: Throwable) { Log.e("GamepadUInput", "sendAxis", t) }
    }
    override fun destroy() {
        try { UInputNative.destroy() } catch (_: Throwable) {}
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
""")

(java / "inject/UInputBridge.kt").write_text("""package com.gamepadbridge.app.inject

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.gamepadbridge.app.ipc.IGamepadUInput
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UInputBridge {
    @Volatile private var remote: IGamepadUInput? = null
    @Volatile private var ready = false
    private var bound = false
    private const val TAG = "GamepadUInput"

    fun start(context: Context): Boolean {
        if (ready && remote != null) return true
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission unavailable")
            return false
        }
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = IGamepadUInput.Stub.asInterface(binder)
                remote = svc
                Thread {
                    try {
                        val opened = svc?.canOpen() == true
                        Log.i(TAG, "/dev/uinput accessible as Shizuku shell: $opened")
                        ready = opened && (svc?.create() == true)
                        Log.i(TAG, "virtual Xbox 360 device created: $ready")
                    } catch (t: Throwable) {
                        Log.e(TAG, "UInput initialization failed", t)
                        ready = false
                    } finally { latch.countDown() }
                }.start()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                remote = null; ready = false
                Log.w(TAG, "UInput user service disconnected")
            }
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, GamepadUInputService::class.java))
            .daemon(false).processNameSuffix("gamepad_uinput").debuggable(false).version(1)
        return try {
            Shizuku.bindUserService(args, connection)
            bound = true
            val ok = latch.await(10, TimeUnit.SECONDS) && ready
            if (!ok) Log.e(TAG, "Virtual pad unavailable: check /dev/uinput permission and SELinux")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t); false
        }
    }

    fun key(androidKeyCode: Int, down: Boolean) {
        if (ready) try { remote?.sendKey(androidKeyCode, down) } catch (t: Throwable) { Log.e(TAG, "key IPC", t) }
    }
    fun axis(androidAxis: Int, value: Float) {
        if (ready) try { remote?.sendAxis(androidAxis, value) } catch (t: Throwable) { Log.e(TAG, "axis IPC", t) }
    }
    fun stop() {
        ready = false
        try { remote?.destroy() } catch (_: Throwable) {}
        remote = null
    }
}
""")

(cpp / "CMakeLists.txt").write_text("""cmake_minimum_required(VERSION 3.22.1)
project(gamepad_uinput)
add_library(gamepad_uinput SHARED gamepad_uinput.cpp)
find_library(log-lib log)
target_link_libraries(gamepad_uinput ${log-lib})
target_compile_options(gamepad_uinput PRIVATE -Wall -Wextra)
""")

(cpp / "gamepad_uinput.cpp").write_text(r"""#include <jni.h>
#include <android/log.h>
#include <linux/uinput.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <cstring>
#include <cerrno>
#include <cstdint>

#define TAG "GamepadUInput"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
static int g_fd = -1;

static int keycode(int k) {
    switch (k) {
        case 96: return BTN_A; case 97: return BTN_B;
        case 99: return BTN_X; case 100: return BTN_Y;
        case 102: return BTN_TL; case 103: return BTN_TR;
        case 104: return BTN_TL2; case 105: return BTN_TR2;
        case 106: return BTN_THUMBL; case 107: return BTN_THUMBR;
        case 108: return BTN_START; case 109: return BTN_SELECT;
        case 110: return BTN_MODE;
        case 19: return BTN_DPAD_UP; case 20: return BTN_DPAD_DOWN;
        case 21: return BTN_DPAD_LEFT; case 22: return BTN_DPAD_RIGHT;
        case 23: return BTN_SOUTH;
        default: return 0;
    }
}
static int axiscode(int a) {
    switch (a) {
        case 0: return ABS_X; case 1: return ABS_Y;
        case 11: return ABS_Z; case 14: return ABS_RZ;
        case 12: return ABS_RX; case 13: return ABS_RY;
        case 15: return ABS_HAT0X; case 16: return ABS_HAT0Y;
        case 17: return ABS_Z; case 18: return ABS_RZ;
        default: return -1;
    }
}
static int emit(uint16_t type, uint16_t code, int32_t value) {
    if (g_fd < 0) return -1;
    input_event e{}; e.type=type; e.code=code; e.value=value;
    if (write(g_fd, &e, sizeof(e)) != (ssize_t)sizeof(e)) return -1;
    return 0;
}
static void emit_sync() { emit(EV_SYN, SYN_REPORT, 0); }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_gamepadbridge_app_inject_UInputNative_canOpen(JNIEnv*, jclass) {
    int fd=open("/dev/uinput", O_WRONLY|O_NONBLOCK);
    if(fd<0){ LOGE("open /dev/uinput: %s", strerror(errno)); return JNI_FALSE; }
    close(fd); return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_gamepadbridge_app_inject_UInputNative_create(JNIEnv*, jclass) {
    if(g_fd>=0){ ioctl(g_fd, UI_DEV_DESTROY); close(g_fd); g_fd=-1; }
    int fd=open("/dev/uinput", O_WRONLY|O_NONBLOCK);
    if(fd<0){ LOGE("open /dev/uinput for create: %s", strerror(errno)); return JNI_FALSE; }
    const int keys[]={BTN_A,BTN_B,BTN_X,BTN_Y,BTN_TL,BTN_TR,BTN_TL2,BTN_TR2,BTN_START,BTN_SELECT,BTN_MODE,BTN_THUMBL,BTN_THUMBR,BTN_DPAD_UP,BTN_DPAD_DOWN,BTN_DPAD_LEFT,BTN_DPAD_RIGHT};
    const int axes[]={ABS_X,ABS_Y,ABS_RX,ABS_RY,ABS_Z,ABS_RZ,ABS_HAT0X,ABS_HAT0Y};
    if(ioctl(fd, UI_SET_EVBIT, EV_KEY)<0 || ioctl(fd, UI_SET_EVBIT, EV_ABS)<0 || ioctl(fd, UI_SET_EVBIT, EV_SYN)<0) goto fail;
    for(int k:keys) if(ioctl(fd,UI_SET_KEYBIT,k)<0) goto fail;
    for(int a:axes){
        if(ioctl(fd,UI_SET_ABSBIT,a)<0) goto fail;
        uinput_abs_setup s{}; s.code=a;
        if(a==ABS_Z || a==ABS_RZ){ s.absinfo.minimum=0; s.absinfo.maximum=255; }
        else if(a==ABS_HAT0X || a==ABS_HAT0Y){ s.absinfo.minimum=-1; s.absinfo.maximum=1; }
        else { s.absinfo.minimum=-32768; s.absinfo.maximum=32767; s.absinfo.flat=128; }
        if(ioctl(fd,UI_ABS_SETUP,&s)<0) goto fail;
    }
    {
        uinput_setup us{}; us.id.bustype=BUS_USB; us.id.vendor=0x045e; us.id.product=0x028e; us.id.version=0x0114;
        std::strncpy(us.name,"Microsoft X-Box 360 pad",UINPUT_MAX_NAME_SIZE-1);
        if(ioctl(fd,UI_DEV_SETUP,&us)<0 || ioctl(fd,UI_DEV_CREATE)<0) goto fail;
    }
    g_fd=fd; LOGE("Virtual Xbox 360 gamepad created"); return JNI_TRUE;
fail:
    LOGE("uinput setup failed: %s",strerror(errno)); close(fd); return JNI_FALSE;
}
static bool dpad_up=false, dpad_down=false, dpad_left=false, dpad_right=false;
extern "C" JNIEXPORT void JNICALL
Java_com_gamepadbridge_app_inject_UInputNative_sendKey(JNIEnv*,jclass,jint androidKey,jboolean down){
    int k=keycode(androidKey); if(k==0) return;
    const bool pressed = down == JNI_TRUE;
    if(androidKey>=19 && androidKey<=22){
        if(androidKey==19) dpad_up=pressed;
        if(androidKey==20) dpad_down=pressed;
        if(androidKey==21) dpad_left=pressed;
        if(androidKey==22) dpad_right=pressed;
        emit(EV_KEY,k,pressed?1:0);
        emit(EV_ABS,ABS_HAT0X,(dpad_right?1:0)-(dpad_left?1:0));
        emit(EV_ABS,ABS_HAT0Y,(dpad_down?1:0)-(dpad_up?1:0));
        emit_sync();
        return;
    }
    emit(EV_KEY,k,pressed?1:0);
    if(androidKey==104 || androidKey==105){
        const int triggerAxis = androidKey==104 ? ABS_Z : ABS_RZ;
        emit(EV_ABS,triggerAxis,pressed?255:0);
    }
    emit_sync();
}
extern "C" JNIEXPORT void JNICALL
Java_com_gamepadbridge_app_inject_UInputNative_sendAxis(JNIEnv*,jclass,jint androidAxis,jfloat value){
    int a=axiscode(androidAxis); if(a<0) return;
    int v;
    if(a==ABS_Z || a==ABS_RZ) v=(int)(value<0?0:(value>1?255:value*255.0f));
    else if(a==ABS_HAT0X || a==ABS_HAT0Y) v=value>0.5f?1:(value< -0.5f?-1:0);
    else v=(int)(value< -1?-32768:(value>1?32767:value*32767.0f));
    if(emit(EV_ABS,a,v)==0) emit_sync();
}
extern "C" JNIEXPORT void JNICALL
Java_com_gamepadbridge_app_inject_UInputNative_destroy(JNIEnv*,jclass){
    if(g_fd>=0){ ioctl(g_fd,UI_DEV_DESTROY); close(g_fd); g_fd=-1; }
}
""")

# Wire the service to the virtual gamepad. Preserve ShizukuBridge only for permission checks.
svc = java / "service/GamepadBridgeService.kt"
s = svc.read_text()
if "import com.gamepadbridge.app.inject.UInputBridge" not in s:
    s = s.replace("import com.gamepadbridge.app.inject.ShizukuBridge", "import com.gamepadbridge.app.inject.ShizukuBridge\nimport com.gamepadbridge.app.inject.UInputBridge")
s = s.replace("        scope.cancel()\n", "        UInputBridge.stop()\n        scope.cancel()\n")
needle = """        if (!ShizukuBridge.hasPermission) {
            stopSelf()
            return
        }
"""
if needle not in s:
    raise SystemExit("Could not find Shizuku permission guard in GamepadBridgeService")
s = s.replace(needle, needle + """        if (!UInputBridge.start(this)) {
            stopSelf()
            return
        }
""", 1)
s = s.replace("ShizukuBridge.injectKey(keyCode, true, InputDevice.SOURCE_GAMEPAD)", "UInputBridge.key(keyCode, true)")
s = s.replace("ShizukuBridge.injectKey(keyCode, false, InputDevice.SOURCE_GAMEPAD)", "UInputBridge.key(keyCode, false)")
s = s.replace("ShizukuBridge.injectAxisBatch(axes, values, InputDevice.SOURCE_JOYSTICK)", """for (i in axes.indices) UInputBridge.axis(axes[i], values[i])""")
svc.write_text(s)

# Add the Shizuku UserService declaration.
manifest = root / "AndroidManifest.xml"
m = manifest.read_text()
if "GamepadUInputService" not in m:
    m = m.replace("</application>", """    <service
        android:name="com.gamepadbridge.app.inject.GamepadUInputService"
        android:exported="true"
        android:process=":gamepad_uinput" />
</application>""")
manifest.write_text(m)

# Enable native CMake + AIDL in either Gradle Kotlin or Groovy DSL.
gradle = Path("app/build.gradle.kts")
if not gradle.exists():
    gradle = Path("app/build.gradle")
g = gradle.read_text()
if gradle.suffix == ".kts":
    if "externalNativeBuild" not in g:
        g = g.replace("android {", """android {
    buildFeatures { aidl = true }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }""", 1)
    if "ndk {" not in g:
        g = g.replace("defaultConfig {", """defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }""", 1)
else:
    if "externalNativeBuild" not in g:
        g = g.replace("android {", """android {
    buildFeatures { aidl true }
    externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt"; version "3.22.1" } }""", 1)
    if "abiFilters" not in g:
        g = g.replace("defaultConfig {", """defaultConfig {
        ndk { abiFilters "arm64-v8a", "armeabi-v7a", "x86_64" }""", 1)
gradle.write_text(g)

# Improve trigger and D-pad compatibility, and add persistent analog dead-zone settings.
settings_store = java / "data/AnalogSettingsStore.kt"
settings_store.write_text("""package com.gamepadbridge.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.analogSettingsDataStore by preferencesDataStore(name = "gamepad_bridge_settings")

data class AnalogSettings(val deadZone: Float = 0.06f)

class AnalogSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    val settingsFlow: Flow<AnalogSettings> = appContext.analogSettingsDataStore.data.map { prefs ->
        AnalogSettings((prefs[DEAD_ZONE] ?: 0.06f).coerceIn(0f, 0.30f))
    }

    suspend fun setDeadZone(value: Float) {
        appContext.analogSettingsDataStore.edit { prefs ->
            prefs[DEAD_ZONE] = value.coerceIn(0f, 0.30f)
        }
    }

    companion object {
        private val DEAD_ZONE = floatPreferencesKey("analog_dead_zone")
    }
}
""")

settings_screen = java / "ui/AnalogSettingsScreen.kt"
settings_screen.write_text("""package com.gamepadbridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamepadbridge.app.data.AnalogSettings
import com.gamepadbridge.app.data.AnalogSettingsStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AnalogSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AnalogSettingsStore(context) }
    val settings by store.settingsFlow.collectAsState(initial = AnalogSettings())
    var deadZone by remember { mutableFloatStateOf(settings.deadZone) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings.deadZone) { deadZone = settings.deadZone }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(18.dp))
        Text("Precisão dos analógicos", style = MaterialTheme.typography.titleMedium)
        Text("Zona morta: \${(deadZone * 100f).roundToInt()}%", style = MaterialTheme.typography.titleLarge)
        Slider(
            value = deadZone,
            onValueChange = { deadZone = it },
            valueRange = 0f..0.30f,
            steps = 29,
            onValueChangeFinished = { scope.launch { store.setDeadZone(deadZone) } }
        )
        Text(
            "Menor valor = resposta mais sensível. Aumente a zona morta se o analógico se mover sozinho. A configuração é salva automaticamente.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { deadZone = 0.06f; scope.launch { store.setDeadZone(0.06f) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restaurar padrão (6%)") }
    }
}
""")

main_activity = java / "MainActivity.kt"
main = main_activity.read_text()
if "AnalogSettingsScreen" not in main:
    import_line = "import com.gamepadbridge.app.ui.MappingScreen"
    if import_line not in main:
        raise SystemExit("MainActivity MappingScreen import anchor missing")
    main = main.replace(import_line, import_line + "\nimport com.gamepadbridge.app.ui.AnalogSettingsScreen", 1)
    toggle_anchor = "                            onToggleService = { starting ->"
    if toggle_anchor not in main:
        raise SystemExit("MainActivity service callback anchor missing")
    main = main.replace(toggle_anchor, '                            onOpenSettings = { navController.navigate("settings") },\n' + toggle_anchor, 1)
    route_anchor = '                    composable("mapping/{profileId}") { entry ->'
    if route_anchor not in main:
        raise SystemExit("MainActivity mapping route anchor missing")
    main = main.replace(route_anchor, '                    composable("settings") { AnalogSettingsScreen { navController.popBackStack() } }\n' + route_anchor, 1)
    signature_anchor = "    onMap: () -> Unit,\n    onToggleService: (Boolean) -> Unit"
    if signature_anchor not in main:
        raise SystemExit("HomeScreen callback signature anchor missing")
    main = main.replace(signature_anchor, "    onMap: () -> Unit,\n    onOpenSettings: () -> Unit,\n    onToggleService: (Boolean) -> Unit", 1)
    home_anchor = '        Text("Gamepad Bridge", style = MaterialTheme.typography.headlineMedium)'
    if home_anchor not in main:
        raise SystemExit("HomeScreen title anchor missing")
    main = main.replace(home_anchor, home_anchor + '\n        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Configurações") }', 1)
    main_activity.write_text(main)

service = java / "service/GamepadBridgeService.kt"
svc_text = service.read_text()
if "AnalogSettingsStore" not in svc_text:
    svc_text = svc_text.replace(
        "import com.gamepadbridge.app.data.ControllerProfile",
        "import com.gamepadbridge.app.data.AnalogSettingsStore\nimport com.gamepadbridge.app.data.ControllerProfile"
    )
    svc_text = svc_text.replace(
        "import kotlinx.coroutines.flow.first",
        "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.collect"
    )
    svc_text = svc_text.replace(
        "    @Volatile private var running = false",
        "    @Volatile private var running = false\n    @Volatile private var analogDeadZone = 0.06f"
    )
    svc_text = svc_text.replace(
        "        val keyMap = HashMap<String, XboxInput>()",
        """        scope.launch {
            AnalogSettingsStore(applicationContext).settingsFlow.collect { analogDeadZone = it.deadZone }
        }

        val keyMap = HashMap<String, XboxInput>()"""
    )
    svc_text = svc_text.replace(
        "                    synchronized(axisValues) { axisValues[axis] = directed }",
        """                    val adjusted = if (isTrigger) directed else applyDeadZone(directed, analogDeadZone)
                    synchronized(axisValues) { axisValues[axis] = adjusted }"""
    )
    helper_anchor = "    private fun parseRawValue(value: String): Int? = runCatching {"
    helper = """    private fun applyDeadZone(value: Float, deadZone: Float): Float {
        val magnitude = kotlin.math.abs(value)
        if (magnitude <= deadZone) return 0f
        val scaled = ((magnitude - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        return kotlin.math.sign(value) * scaled
    }

"""
    if helper_anchor not in svc_text:
        raise SystemExit("Service dead-zone helper anchor missing")
    svc_text = svc_text.replace(helper_anchor, helper + helper_anchor, 1)
    service.write_text(svc_text)

print("Applied trigger/D-pad fixes and analog settings.")

print("UInput virtual Xbox 360 integration applied")
