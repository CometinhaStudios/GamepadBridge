from pathlib import Path

bridge = Path("app/src/main/java/com/gamepadbridge/app/inject/ShizukuBridge.kt")
s = bridge.read_text()
if "suspend fun awaitInjector" not in s:
    marker = "    fun requestPermission() {"
    addition = """    /**
     * Wait until the Shizuku UserService is actually connected before the bridge
     * starts reading controller events. Permission alone does not mean the
     * injector binder is ready; earlier calls could silently be dropped.
     */
    suspend fun awaitInjector(context: Context, timeoutMs: Long = 8000L): Boolean {
        if (!hasPermission) return false
        bind(context.applicationContext)
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (injector != null) return@withTimeoutOrNull true
                if (!isAvailable || !hasPermission) return@withTimeoutOrNull false
                bind(context.applicationContext)
                kotlinx.coroutines.delay(100)
            }
            false
        } ?: false
    }

"""
    if marker not in s:
        raise SystemExit("Could not find ShizukuBridge insertion point")
    s = s.replace(marker, addition + marker, 1)
    bridge.write_text(s)

service = Path("app/src/main/java/com/gamepadbridge/app/service/GamepadBridgeService.kt")
s = service.read_text()
old = """        if (!ShizukuBridge.hasPermission) {
            stopSelf()
            return
        }"""
new = """        // Shizuku permission may be granted while its injector UserService is
        // still binding. Do not start the bridge until the binder is ready.
        if (!ShizukuBridge.hasPermission || !ShizukuBridge.awaitInjector(this)) {
            stopSelf()
            return
        }"""
if old in s:
    s = s.replace(old, new, 1)
elif "ShizukuBridge.awaitInjector(this)" not in s:
    raise SystemExit("Could not find bridge permission guard")
service.write_text(s)
print("Added injector readiness wait before starting controller event forwarding.")
