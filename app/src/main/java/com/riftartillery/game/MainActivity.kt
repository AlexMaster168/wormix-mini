package com.riftartillery.game

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.Window
import android.view.FrameMetrics
import android.os.HandlerThread
import android.os.Handler

class MainActivity : Activity() {
    private lateinit var gameView: BattleView
    private val frameStats=FrameStats()
    private val frameThread=HandlerThread("RiftFrameMetrics")
    private val frameListener=Window.OnFrameMetricsAvailableListener {_,metrics,_->
        if(::gameView.isInitialized && gameView.isBattleRunning() && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0L) frameStats.record(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
    }
    fun performanceReport()=frameStats.report()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = BattleView(this)
        setContentView(gameView)
        hideSystemBars()
        frameThread.start()
        window.addOnFrameMetricsAvailableListener(frameListener,Handler(frameThread.looper))
    }
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if(Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus) hideSystemBars()}
    override fun onPause() { super.onPause(); gameView.suspendGame() }
    override fun onResume() { super.onResume(); if(::gameView.isInitialized) gameView.resumeGame() }
    override fun onDestroy() {window.removeOnFrameMetricsAvailableListener(frameListener);frameThread.quitSafely();gameView.release();super.onDestroy()}
    @Deprecated("Platform back compatibility")
    override fun onBackPressed() { gameView.back() }
}
