package com.gamepadbridge.preview
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

class MainActivity : Activity() {
    private lateinit var screen: GamepadPreviewView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        screen = GamepadPreviewView(this)
        setContentView(screen)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::screen.isInitialized && screen.handleKey(event)) return true
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::screen.isInitialized && screen.handleMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::screen.isInitialized && screen.showHome()) return
        super.onBackPressed()
    }
}
