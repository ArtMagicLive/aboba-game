package com.aboba.destruction.game

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView + игровой поток. Вся логика — в GameWorld.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val world = GameWorld(context)
    private var thread: GameThread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        world.resize(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
        if (thread == null) {
            thread = GameThread(holder, world).also { it.start() }
        }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        world.resize(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        thread?.stopLoop()
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            world.onTap(event.x, event.y)
            return true
        }
        return true
    }

    fun saveProgress() = world.save()

    fun handleBack(): Boolean {
        return when (world.state) {
            GameState.PLAYING -> {
                world.state = GameState.PAUSED
                true
            }
            GameState.PAUSED -> {
                world.state = GameState.PLAYING
                true
            }
            GameState.SHOP, GameState.GAME_OVER -> {
                world.state = GameState.MENU
                true
            }
            else -> false
        }
    }
}

/**
 * Игровой цикл: обновление логики + отрисовка на Canvas.
 */
class GameThread(
    private val holder: SurfaceHolder,
    private val world: GameWorld
) : Thread() {

    @Volatile
    private var running = true

    fun stopLoop() {
        running = false
        try {
            join(600)
        } catch (_: InterruptedException) {
        }
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt < 0f) dt = 0f
            if (dt > 0.05f) dt = 0.05f

            world.update(dt)

            val canvas = try {
                holder.lockCanvas()
            } catch (t: Throwable) {
                null
            }

            if (canvas != null) {
                try {
                    world.draw(canvas)
                } catch (t: Throwable) {
                    // пропускаем кадр
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (t: Throwable) {
                    }
                }
            }

            val frameTime = System.nanoTime() - now
            val sleepMs = (16_000_000L - frameTime) / 1_000_000L
            if (sleepMs > 0) {
                try {
                    sleep(sleepMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
