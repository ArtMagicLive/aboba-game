package com.aboba.destruction

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import com.aboba.destruction.game.GameView

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.saveProgress()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.handleBack()) {
            super.onBackPressed()
        }
    }
}
