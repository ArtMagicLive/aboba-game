package com.aboba.destruction.game

import android.graphics.PointF
import kotlin.random.Random

enum class GameState { MENU, PLAYING, PAUSED, GAME_OVER, SHOP }

/**
 * Типы солдатиков: здоровье, размер, скорость, награда.
 */
enum class SoldierType(
    val maxHp: Int,
    val scale: Float,
    val speed: Float,
    val coin: Int,
    val points: Int,
    val color: Int
) {
    NORMAL(1, 1.00f, 55f, 1, 10, 0xFF7EC8E3.toInt()),
    FAT(4, 1.55f, 34f, 4, 35, 0xFFF4A261.toInt()),
    FAST(1, 0.85f, 110f, 2, 20, 0xFFB39DDB.toInt()),
    ZOMBIE(3, 1.05f, 40f, 3, 30, 0xFF9CCC65.toInt()),
    BOSS(22, 2.40f, 26f, 30, 250, 0xFFEF5350.toInt())
}

class Soldier(
    val type: SoldierType,
    var x: Float,
    var y: Float
) {
    var hp: Int = type.maxHp
    var alive: Boolean = true
    var kvx: Float = 0f          // скорость от knockback
    var kvy: Float = 0f
    var panic: Float = 0f
    var flash: Float = 0f
    var phase: Float = Random.nextFloat() * 10f

    val w: Float get() = 20f * type.scale
    val h: Float get() = 34f * type.scale
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var size: Float,
    val color: Int,
    val gravity: Float
)

class Debris(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var size: Float,
    val color: Int,
    var rot: Float,
    var vr: Float
)

class Floater(
    var x: Float,
    var y: Float,
    var vy: Float,
    var life: Float,
    val text: String,
    val color: Int,
    val size: Float
)

class Ring(
    var x: Float,
    var y: Float,
    val r: Float,
    var life: Float = 1f
)

class Meteor(
    var x: Float,
    var y: Float,
    var vy: Float,
    val tx: Float,
    val ty: Float,
    val power: Float
)

class Zap(
    var life: Float,
    val pts: List<PointF>
)

class Hole(
    var x: Float,
    var y: Float,
    var life: Float
)
