package com.aboba.destruction.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.aboba.destruction.Prefs
import com.aboba.destruction.audio.SoundFx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Вся игра: волны солдатиков, оружие, физика, апгрейды, отрисовка.
 */
class GameWorld(private val context: Context) {

    // ---------- экран ----------
    var W = 0f
    var H = 0f
    var d = 1f
    var groundY = 0f
    var shrineX = 0f

    // ---------- состояние ----------
    var state: GameState = GameState.MENU
    val prefs = Prefs(context)
    val sfx = SoundFx(context)
    var bank: Int = prefs.bank

    // ---------- сущности ----------
    private val soldiers = ArrayList<Soldier>()
    private val particles = ArrayList<Particle>()
    private val debris = ArrayList<Debris>()
    private val floaters = ArrayList<Floater>()
    private val rings = ArrayList<Ring>()
    private val meteors = ArrayList<Meteor>()
    private val zaps = ArrayList<Zap>()
    private val holes = ArrayList<Hole>()

    // ---------- прогресс забега ----------
    var wave = 0
    var toSpawn = 0
    var spawnTimer = 0f
    var breakTimer = 1.6f
    var score = 0
    var kills = 0
    var combo = 0
    var comboTimer = 0f
    var shrineHp = 5
    val shrineMax = 5
    var shrineHurt = 0f
    var shake = 0f
    var flash = 0f
    var time = 0f
    var weapon = 0
    val ammo = floatArrayOf(0f, 0f, 0f, 0f)
    var endBanner = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        private const val SKY_TOP = 0xFF7FD8FF.toInt()
        private const val SKY_BOT = 0xFFD9F6FF.toInt()
        private const val GRASS = 0xFF6FD46F.toInt()
        private const val GRASS_DARK = 0xFF4FBF5A.toInt()
        private const val SUN = 0xFFFFE066.toInt()
        private const val ACCENT = 0xFFFF7043.toInt()
        private const val ACCENT_DARK = 0xFFE64A19.toInt()
        private const val YELLOW = 0xFFFFD54F.toInt()
        private const val INK = 0xFF3A2E1F.toInt()
        private const val CARD = 0xFFFFFFFF.toInt()
        private const val GREEN = 0xFF43A047.toInt()
        private const val RED = 0xFFE53935.toInt()

        private val ABOBA = arrayOf(
            "ва!", "абоба", "67", "мими-мимиу", "ВААА", "ой!", "мама", "🗿", "бум!"
        )
    }

    init {
        sfx.enabled = prefs.sound
    }

    // =====================================================================
    //  ЖИЗНЕННЫЙ ЦИКЛ
    // =====================================================================

    fun resize(w: Float, h: Float, density: Float) {
        W = w
        H = h
        d = density
        groundY = H - 150f * d
        shrineX = W / 2f
    }

    fun save() {
        prefs.bank = bank
    }

    private fun startGame() {
        soldiers.clear()
        particles.clear()
        debris.clear()
        floaters.clear()
        rings.clear()
        meteors.clear()
        zaps.clear()
        holes.clear()

        wave = 0
        toSpawn = 0
        spawnTimer = 0f
        breakTimer = 1.4f
        score = 0
        kills = 0
        combo = 0
        comboTimer = 0f
        shrineHp = shrineMax
        shrineHurt = 0f
        shake = 0f
        flash = 0f
        endBanner = 0f
        weapon = 0
        for (i in 0..3) ammo[i] = maxAmmo()
        state = GameState.PLAYING
    }

    private fun gameOver() {
        state = GameState.GAME_OVER
        endBanner = 0f
        save()
        prefs.totalKills = prefs.totalKills + kills
        if (wave > prefs.bestWave) prefs.bestWave = wave
        if (score > prefs.bestScore) prefs.bestScore = score
        sfx.playHurt()
        flash = 0.85f
    }

    // =====================================================================
    //  УЛУЧШЕНИЯ
    // =====================================================================

    private fun damage(): Int = 1 + prefs.upgrade(1)
    private fun radiusMul(): Float = 1f + 0.09f * prefs.upgrade(0)
    private fun maxAmmo(): Float = 2f + prefs.upgrade(2)
    private fun rechargeRate(): Float = 0.13f + 0.05f * prefs.upgrade(3)
    private fun upgradeCost(level: Int): Int = 50 + level * 80

    // =====================================================================
    //  ВВОД
    // =====================================================================

    fun onTap(x: Float, y: Float) {
        when (state) {
            GameState.MENU -> {
                if (menuPlay().contains(x, y)) {
                    sfx.playClick(); startGame()
                } else if (menuShop().contains(x, y)) {
                    sfx.playClick(); state = GameState.SHOP
                } else if (menuSound().contains(x, y)) {
                    prefs.sound = !prefs.sound
                    sfx.enabled = prefs.sound
                    sfx.playClick()
                }
            }

            GameState.PLAYING -> {
                if (hudPause().contains(x, y)) {
                    sfx.playClick(); state = GameState.PAUSED; return
                }
                for (i in 0..4) {
                    if (weaponBtn(i).contains(x, y)) {
                        sfx.playClick(); weapon = i; return
                    }
                }
                if (y > H - 104f * d) return
                useWeapon(x, y)
            }

            GameState.PAUSED -> {
                if (pauseResume().contains(x, y)) {
                    sfx.playClick(); state = GameState.PLAYING
                } else if (pauseRestart().contains(x, y)) {
                    sfx.playClick(); startGame()
                } else if (pauseMenu().contains(x, y)) {
                    sfx.playClick(); save(); state = GameState.MENU
                }
            }

            GameState.GAME_OVER -> {
                if (overRestart().contains(x, y)) {
                    sfx.playClick(); startGame()
                } else if (overShop().contains(x, y)) {
                    sfx.playClick(); state = GameState.SHOP
                } else if (overMenu().contains(x, y)) {
                    sfx.playClick(); state = GameState.MENU
                }
            }

            GameState.SHOP -> {
                for (i in 0..3) {
                    if (shopBuy(i).contains(x, y)) {
                        buyUpgrade(i); return
                    }
                }
                if (shopBack().contains(x, y)) {
                    sfx.playClick(); save(); state = GameState.MENU
                }
            }
        }
    }

    private fun buyUpgrade(index: Int) {
        val level = prefs.upgrade(index)
        if (level >= 8) {
            sfx.playDenyClick()
            return
        }
        val cost = upgradeCost(level)
        if (bank >= cost) {
            bank -= cost
            prefs.setUpgrade(index, level + 1)
            prefs.bank = bank
            sfx.playCoin()
            addFloater(W / 2f, H * 0.5f, "УЛУЧШЕНО!", GREEN, 26f)
        } else {
            sfx.playDenyClick()
        }
    }

    private fun SoundFx.playDenyClick() {
        playClick()
    }

    // =====================================================================
    //  ОБНОВЛЕНИЕ
    // =====================================================================

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt

        if (state == GameState.PLAYING) updatePlay(dt)
        updateFx(dt)

        shake *= 0.85f
        if (shake < 0.6f) shake = 0f
        flash = max(0f, flash - dt * 2.6f)
        shrineHurt = max(0f, shrineHurt - dt * 3f)
        endBanner += dt
    }

    private fun updatePlay(dt: Float) {
        // патроны восстанавливаются
        val regen = rechargeRate() * dt
        for (i in 0..3) {
            if (ammo[i] < maxAmmo()) ammo[i] = min(maxAmmo(), ammo[i] + regen)
        }

        // комбо
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }

        // волны
        if (toSpawn > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = max(0.20f, 0.95f - wave * 0.03f)
                spawnSoldier()
                toSpawn--
            }
        } else if (soldiers.isEmpty()) {
            breakTimer -= dt
            if (breakTimer <= 0f) {
                startWave()
                breakTimer = 2.0f
            }
        }

        // солдатики
        for (s in soldiers) updateSoldier(s, dt)
        soldiers.removeAll { !it.alive }

        // метеоры
        val mi = meteors.iterator()
        while (mi.hasNext()) {
            val m = mi.next()
            m.y += m.vy * dt
            particles.add(
                Particle(
                    m.x + (Random.nextFloat() - 0.5f) * 12f * d,
                    m.y,
                    (Random.nextFloat() - 0.5f) * 60f * d,
                    -Random.nextFloat() * 120f * d,
                    0.6f, 7f * d, 0xFFFF8A65.toInt(), -50f * d
                )
            )
            if (m.y >= m.ty) {
                explode(m.tx, m.ty, 130f * d * m.power, 900f * d * m.power, damage() + 2, true)
                shake = max(shake, 26f * d * m.power)
                sfx.playBoom()
                mi.remove()
            }
        }

        // чёрные дыры
        val hi = holes.iterator()
        while (hi.hasNext()) {
            val h = hi.next()
            h.life -= dt
            val pullR = 280f * d
            for (s in soldiers) {
                if (!s.alive) continue
                val dx = h.x - s.x
                val dy = h.y - s.y
                val dist = sqrt(dx * dx + dy * dy) + 0.001f
                if (dist < pullR) {
                    val f = (1f - dist / pullR) * 1400f * d
                    s.kvx += dx / dist * f * dt
                    s.kvy += dy / dist * f * dt
                    if (Random.nextFloat() < 3.5f * dt) {
                        damageSoldier(s, damage())
                    }
                }
            }
            if (h.life <= 0f) hi.remove()
        }
    }

    private fun startWave() {
        wave++
        toSpawn = min(28, 4 + wave * 2)
        spawnTimer = 0f
        if (wave % 5 == 0) spawnBoss()
        addFloater(W / 2f, groundY - 230f * d, "ВОЛНА $wave", INK, 34f)
    }

    private fun spawnInterval(): Float = max(0.20f, 0.95f - wave * 0.03f)

    private fun spawnSoldier() {
        val r = Random.nextFloat()
        val type = when {
            wave < 2 -> SoldierType.NORMAL
            wave < 4 -> if (r < 0.75f) SoldierType.NORMAL else SoldierType.FAT
            wave < 7 -> when {
                r < 0.50f -> SoldierType.NORMAL
                r < 0.75f -> SoldierType.FAT
                else -> SoldierType.FAST
            }
            else -> when {
                r < 0.35f -> SoldierType.NORMAL
                r < 0.55f -> SoldierType.FAT
                r < 0.75f -> SoldierType.FAST
                else -> SoldierType.ZOMBIE
            }
        }
        spawnType(type)
    }

    private fun spawnBoss() {
        spawnType(SoldierType.BOSS)
    }

    private fun spawnType(type: SoldierType) {
        val fromLeft = Random.nextBoolean()
        val x = if (fromLeft) -40f * d else W + 40f * d
        val s = Soldier(type, x, groundY - 20f * d)
        soldiers.add(s)
    }

    private fun updateSoldier(s: Soldier, dt: Float) {
        if (s.flash > 0f) s.flash -= dt * 4f
        if (s.panic > 0f) s.panic -= dt

        val half = s.h * 0.5f * d
        val grounded = s.y >= groundY - half - 0.5f

        if (grounded) {
            s.y = groundY - half
            if (s.kvy > 0f) s.kvy = 0f
            s.kvx *= (1f - min(1f, 7f * dt))
            if (abs(s.kvx) < 6f * d) s.kvx = 0f

            if (s.panic <= 0f) {
                val dir = if (shrineX > s.x) 1f else -1f
                s.x += dir * s.type.speed * d * dt
                s.phase += dt * (5f + s.type.speed * 0.05f)
            }
        } else {
            s.y += s.kvy * dt
            s.kvy += 1600f * d * dt
        }

        s.x += s.kvx * dt

        if (s.x < -100f * d) s.x = -100f * d
        if (s.x > W + 100f * d) s.x = W + 100f * d

        // дошёл до абобы — отнимает жизнь
        if (abs(s.x - shrineX) < 34f * d && s.y + half >= groundY - 8f * d) {
            s.alive = false
            shrineHp--
            shrineHurt = 1f
            shake = max(shake, 10f * d)
            sfx.playHurt()
            addFloater(shrineX, groundY - 130f * d, "-1", RED, 30f)
            if (shrineHp <= 0) gameOver()
        }
    }

    // =====================================================================
    //  ОРУЖИЕ
    // =====================================================================

    private fun spend(index: Int): Boolean {
        if (index == 0) return true
        val i = index - 1
        return if (ammo[i] >= 1f) {
            ammo[i] -= 1f
            true
        } else {
            sfx.playClick()
            addFloater(W / 2f, H - 170f * d, "НЕТ ЗАРЯДА", RED, 22f)
            false
        }
    }

    private fun useWeapon(x: Float, y: Float) {
        when (weapon) {
            0 -> {
                explode(x, y, 80f * d * radiusMul(), 520f * d, damage(), false)
                sfx.playBoom()
            }
            1 -> {
                if (!spend(1)) return
                lightning(x, y)
            }
            2 -> {
                if (!spend(2)) return
                meteors.add(Meteor(x, -50f * d, 760f * d, x, groundY, 1.6f * radiusMul()))
                sfx.playZap()
            }
            3 -> {
                if (!spend(3)) return
                holes.add(Hole(x, y, 2.4f))
                sfx.playZap()
            }
            4 -> {
                if (!spend(4)) return
                nuke()
            }
        }
    }

    private fun explode(
        cx: Float,
        cy: Float,
        radius: Float,
        force: Float,
        dmg: Int,
        big: Boolean
    ) {
        rings.add(Ring(cx, cy, radius, 1f))
        shake = max(shake, if (big) radius * 0.30f else radius * 0.18f)

        val count = (radius / d * 1.2f).toInt().coerceIn(14, 90)
        for (i in 0 until count) {
            val ang = Random.nextFloat() * (Math.PI * 2).toFloat()
            val sp = Random.nextFloat() * force * 0.9f
            val col = when {
                Random.nextFloat() < 0.4f -> 0xFFFFF176.toInt()
                Random.nextFloat() < 0.7f -> 0xFFFFB74D.toInt()
                else -> 0xFFFF7043.toInt()
            }
            particles.add(
                Particle(
                    cx, cy,
                    kotlin.math.cos(ang) * sp,
                    kotlin.math.sin(ang) * sp,
                    0.7f + Random.nextFloat() * 0.6f,
                    (3f + Random.nextFloat() * 7f) * d,
                    col,
                    420f * d
                )
            )
        }

        for (s in soldiers) {
            if (!s.alive) continue
            val dx = s.x - cx
            val dy = s.y - cy
            val dist = sqrt(dx * dx + dy * dy) + 0.001f
            if (dist < radius) {
                val falloff = 1f - dist / radius
                s.kvx += dx / dist * force * falloff
                s.kvy += dy / dist * force * falloff - force * 0.75f * falloff
                s.panic = 1.6f
                damageSoldier(s, dmg)
            }
        }
    }

    private fun lightning(x: Float, y: Float) {
        val pts = ArrayList<PointF>()
        var cx = x
        var cy = 0f
        pts.add(PointF(cx, cy))
        while (cy < y) {
            cy += 22f * d
            cx += (Random.nextFloat() - 0.5f) * 36f * d
            pts.add(PointF(cx, cy))
        }
        pts.add(PointF(x, y))
        zaps.add(Zap(1f, pts))

        flash = max(flash, 0.30f)
        shake = max(shake, 8f * d)

        val r = 96f * d * radiusMul()
        for (s in soldiers) {
            if (!s.alive) continue
            val dx = s.x - x
            val dy = s.y - y
            if (dx * dx + dy * dy < r * r) {
                s.panic = 1.5f
                s.kvx += (Random.nextFloat() - 0.5f) * 400f * d
                s.kvy -= 340f * d
                damageSoldier(s, damage() + 1)
            }
        }

        for (i in 0 until 22) {
            particles.add(
                Particle(
                    x + (Random.nextFloat() - 0.5f) * 60f * d,
                    y + (Random.nextFloat() - 0.5f) * 60f * d,
                    (Random.nextFloat() - 0.5f) * 500f * d,
                    (Random.nextFloat() - 0.5f) * 500f * d,
                    0.8f, 4f * d, 0xFFFFF176.toInt(), 260f * d
                )
            )
        }
        sfx.playZap()
    }

    private fun nuke() {
        flash = 1f
        shake = 44f * d
        val cx = W / 2f
        val cy = groundY - 130f * d
        for (i in 0 until 3) {
            explode(
                cx + (Random.nextFloat() - 0.5f) * 240f * d,
                cy + (Random.nextFloat() - 0.5f) * 180f * d,
                320f * d,
                1500f * d,
                damage() + 6,
                true
            )
        }
        sfx.playBoom()
    }

    // =====================================================================
    //  УРОН И СМЕРТЬ
    // =====================================================================

    private fun damageSoldier(s: Soldier, dmg: Int) {
        if (!s.alive) return
        s.hp -= dmg
        s.flash = 1f
        if (s.hp <= 0) killSoldier(s)
    }

    private fun killSoldier(s: Soldier) {
        s.alive = false
        kills++
        combo++
        comboTimer = 1.6f
        val mult = 1 + combo / 8
        score += s.type.points * mult
        bank += s.type.coin

        for (i in 0 until 8) {
            debris.add(
                Debris(
                    s.x, s.y,
                    (Random.nextFloat() - 0.5f) * 460f * d,
                    -Random.nextFloat() * 460f * d,
                    1f,
                    (3f + Random.nextFloat() * 5f) * d,
                    s.type.color,
                    Random.nextFloat() * 6f,
                    (Random.nextFloat() - 0.5f) * 14f
                )
            )
        }
        rings.add(Ring(s.x, s.y, 26f * d, 1f))
        addFloater(
            s.x, s.y - 22f * d,
            ABOBA[Random.nextInt(ABOBA.size)],
            INK, 18f
        )
        sfx.playPop()
    }

    private fun addFloater(x: Float, y: Float, text: String, color: Int, size: Float) {
        floaters.add(Floater(x, y, -60f * d, 1.2f, text, color, size * d))
    }

    // =====================================================================
    //  ЭФФЕКТЫ
    // =====================================================================

    private fun updateFx(dt: Float) {
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val q = pi.next()
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vy += q.gravity * dt
            q.vx *= (1f - 2.5f * dt).coerceIn(0f, 1f)
            q.life -= dt * 1.6f
            if (q.life <= 0f) pi.remove()
        }

        val di = debris.iterator()
        while (di.hasNext()) {
            val q = di.next()
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vy += 1000f * d * dt
            q.rot += q.vr * dt
            q.life -= dt * 0.75f
            if (q.y > groundY - 4f * d) {
                q.y = groundY - 4f * d
                q.vy = -abs(q.vy) * 0.35f
                q.vx *= 0.75f
            }
            if (q.life <= 0f) di.remove()
        }

        val fi = floaters.iterator()
        while (fi.hasNext()) {
            val q = fi.next()
            q.y += q.vy * dt
            q.life -= dt
            if (q.life <= 0f) fi.remove()
        }

        val ri = rings.iterator()
        while (ri.hasNext()) {
            val q = ri.next()
            q.life -= dt * 1.8f
            if (q.life <= 0f) ri.remove()
        }

        val zi = zaps.iterator()
        while (zi.hasNext()) {
            val q = zi.next()
            q.life -= dt * 3.5f
            if (q.life <= 0f) zi.remove()
        }
    }

    // =====================================================================
    //  ОТРИСОВКА
    // =====================================================================

    fun draw(c: Canvas) {
        if (W <= 0f || H <= 0f) return

        c.save()
        val sx = (Random.nextFloat() - 0.5f) * shake
        val sy = (Random.nextFloat() - 0.5f) * shake
        c.translate(sx, sy)

        drawSky(c)
        drawGround(c)

        if (state != GameState.SHOP) {
            drawShrine(c)
            for (s in soldiers) drawSoldier(c, s)
        }

        drawFx(c)
        c.restore()

        if (flash > 0f) {
            p.style = Paint.Style.FILL
            p.color = Color.argb((flash * 200f).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawRect(0f, 0f, W, H, p)
        }

        when (state) {
            GameState.MENU -> drawMenu(c)
            GameState.PLAYING -> drawHud(c)
            GameState.PAUSED -> {
                drawHud(c)
                drawPauseOverlay(c)
            }
            GameState.GAME_OVER -> {
                drawHud(c)
                drawGameOverOverlay(c)
            }
            GameState.SHOP -> drawShop(c)
        }
    }

    // ---------- фон ----------

    private fun drawSky(c: Canvas) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, H, SKY_TOP, SKY_BOT, Shader.TileMode.CLAMP)
        c.drawRect(-80f, -80f, W + 80f, H + 80f, p)
        p.shader = null

        p.color = SUN
        c.drawCircle(W * 0.82f, H * 0.11f, 46f * d, p)

        p.color = 0xFFFFFFFF.toInt()
        for (i in 0 until 4) {
            val cx = ((time * (14f + i * 7f) + i * 230f) * d) % (W + 340f * d) - 170f * d
            val cy = H * (0.07f + i * 0.055f)
            drawCloud(c, cx, cy, (0.75f + i * 0.14f) * d)
        }

        // холмы
        p.color = 0xFF9BE29B.toInt()
        c.drawCircle(W * 0.18f, groundY + 30f * d, 190f * d, p)
        c.drawCircle(W * 0.78f, groundY + 40f * d, 230f * d, p)
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, scale: Float) {
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, 26f * scale, p)
        c.drawCircle(x + 30f * scale, y + 6f * scale, 20f * scale, p)
        c.drawCircle(x - 30f * scale, y + 8f * scale, 18f * scale, p)
        c.drawCircle(x + 6f * scale, y - 14f * scale, 20f * scale, p)
    }

    private fun drawGround(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = GRASS
        c.drawRect(-80f, groundY, W + 80f, H + 80f, p)
        p.color = GRASS_DARK
        c.drawRect(-80f, groundY, W + 80f, groundY + 10f * d, p)
    }

    // ---------- абоба-тотем ----------

    private fun drawShrine(c: Canvas) {
        val wob = sin(time * 2f) * 3f * d
        val hurtShift = if (shrineHurt > 0f) (Random.nextFloat() - 0.5f) * 10f * d * shrineHurt else 0f
        val w = 112f * d
        val h = 98f * d

        c.save()
        c.translate(shrineX + hurtShift, groundY - h / 2f + wob)

        p.style = Paint.Style.FILL
        p.color = if (shrineHurt > 0.3f) 0xFFFF8A65.toInt() else YELLOW
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, 28f * d, 28f * d, p)

        p.color = INK
        c.drawCircle(-23f * d, -14f * d, 8f * d, p)
        c.drawCircle(23f * d, -14f * d, 8f * d, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = INK
        c.drawArc(RectF(-24f * d, -2f * d, 24f * d, 30f * d), 20f, 140f, false, p)
        p.style = Paint.Style.FILL

        c.restore()

        // сердечки здоровья
        for (i in 0 until shrineMax) {
            val filled = i < shrineHp
            val hx = shrineX - (shrineMax - 1) * 20f * d / 2f + i * 20f * d
            val hy = groundY - h - 30f * d
            drawHeart(c, hx, hy, 8f * d, if (filled) RED else 0xFFE0E0E0.toInt())
        }
    }

    private fun drawHeart(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x - r * 0.55f, y, r * 0.75f, p)
        c.drawCircle(x + r * 0.55f, y, r * 0.75f, p)
        val path = Path()
        path.moveTo(x - r * 1.25f, y + r * 0.25f)
        path.lineTo(x, y + r * 1.7f)
        path.lineTo(x + r * 1.25f, y + r * 0.25f)
        path.close()
        c.drawPath(path, p)
    }

    // ---------- солдатики ----------

    private fun drawSoldier(c: Canvas, s: Soldier) {
        val w = s.w * d
        val h = s.h * d
        val baseColor = if (s.flash > 0f) blend(s.type.color, 0xFFFFFFFF.toInt(), s.flash.coerceIn(0f, 1f)) else s.type.color

        c.save()
        c.translate(s.x, s.y)

        // тень
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawOval(RectF(-w * 0.6f, h * 0.42f, w * 0.6f, h * 0.62f), p)

        // ноги
        val swing = sin(s.phase) * 4f * d
        p.color = 0xFF4A4A4A.toInt()
        c.drawRoundRect(-w * 0.32f, h * 0.16f, -w * 0.06f, h * 0.50f + swing, 4f * d, 4f * d, p)
        c.drawRoundRect(w * 0.06f, h * 0.16f, w * 0.32f, h * 0.50f - swing, 4f * d, 4f * d, p)

        // тело
        p.color = baseColor
        c.drawRoundRect(-w * 0.44f, -h * 0.22f, w * 0.44f, h * 0.30f, 9f * d, 9f * d, p)

        // руки
        if (s.panic > 0f) {
            c.drawRoundRect(-w * 0.66f, -h * 0.58f, -w * 0.42f, -h * 0.06f, 6f * d, 6f * d, p)
            c.drawRoundRect(w * 0.42f, -h * 0.58f, w * 0.66f, -h * 0.06f, 6f * d, 6f * d, p)
        } else {
            c.drawRoundRect(-w * 0.64f, -h * 0.16f, -w * 0.42f, h * 0.22f, 6f * d, 6f * d, p)
            c.drawRoundRect(w * 0.42f, -h * 0.16f, w * 0.64f, h * 0.22f, 6f * d, 6f * d, p)
        }

        // голова
        p.color = 0xFFFFD9B3.toInt()
        c.drawCircle(0f, -h * 0.44f, w * 0.44f, p)

        // каска
        p.color = if (s.type == SoldierType.BOSS) 0xFFFFC107.toInt() else 0xFF5E8C4A.toInt()
        c.drawArc(
            RectF(-w * 0.48f, -h * 0.64f, w * 0.48f, -h * 0.24f),
            180f, 180f, true, p
        )

        // глаза
        p.color = INK
        val ey = -h * 0.46f
        val er = if (s.panic > 0f) w * 0.10f else w * 0.065f
        c.drawCircle(-w * 0.17f, ey, er, p)
        c.drawCircle(w * 0.17f, ey, er, p)

        // полоска здоровья
        if (s.type.maxHp > 1 && s.hp < s.type.maxHp) {
            val bw = w * 1.15f
            p.color = 0x55000000
            c.drawRoundRect(-bw / 2f, -h * 0.98f, bw / 2f, -h * 0.84f, 5f * d, 5f * d, p)
            p.color = 0xFF66BB6A.toInt()
            val f = s.hp.toFloat() / s.type.maxHp
            c.drawRoundRect(-bw / 2f, -h * 0.98f, -bw / 2f + bw * f, -h * 0.84f, 5f * d, 5f * d, p)
        }

        c.restore()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b)
        return Color.rgb(
            (ar + (br - ar) * t).toInt(),
            (ag + (bg - ag) * t).toInt(),
            (ab + (bb - ab) * t).toInt()
        )
    }

    // ---------- эффекты ----------

    private fun drawFx(c: Canvas) {
        // кольца взрывов
        for (r in rings) {
            val k = 1f - r.life
            p.style = Paint.Style.STROKE
            p.strokeWidth = (3f + 8f * r.life) * d
            p.color = Color.argb(
                (r.life * 210f).toInt().coerceIn(0, 255),
                255, 220, 120
            )
            c.drawCircle(r.x, r.y, r.r * (0.35f + k * 1.25f), p)
        }
        p.style = Paint.Style.FILL

        // обломки
        for (q in debris) {
            p.color = q.color
            p.alpha = (q.life.coerceIn(0f, 1f) * 255).toInt()
            c.save()
            c.translate(q.x, q.y)
            c.rotate(q.rot * 57.29578f)
            c.drawRect(-q.size / 2f, -q.size / 2f, q.size / 2f, q.size / 2f, p)
            c.restore()
        }
        p.alpha = 255

        // частицы
        for (q in particles) {
            p.color = q.color
            p.alpha = (q.life.coerceIn(0f, 1f) * 255).toInt()
            c.drawCircle(q.x, q.y, q.size * q.life.coerceIn(0.15f, 1f), p)
        }
        p.alpha = 255

        // метеоры
        for (m in meteors) {
            p.color = 0xFFFF7043.toInt()
            c.drawCircle(m.x, m.y, 18f * d, p)
            p.color = 0xFFFFF176.toInt()
            c.drawCircle(m.x, m.y, 10f * d, p)
        }

        // чёрные дыры
        for (h in holes) {
            val k = h.life / 2.4f
            p.color = 0xFF1A1A1A.toInt()
            c.drawCircle(h.x, h.y, 36f * d * k.coerceIn(0.2f, 1f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f * d
            p.color = 0xFF7E57C2.toInt()
            c.drawCircle(h.x, h.y, (46f + sin(time * 9f) * 6f) * d * k.coerceIn(0.2f, 1f), p)
            p.style = Paint.Style.FILL
        }

        // молнии
        for (z in zaps) {
            if (z.pts.size < 2) continue
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = 8f * d
            p.color = Color.argb((z.life * 90f).toInt().coerceIn(0, 255), 255, 235, 59)
            drawPolyline(c, z.pts)
            p.strokeWidth = 3.5f * d
            p.color = Color.argb((z.life * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            drawPolyline(c, z.pts)
            p.style = Paint.Style.FILL
        }

        // всплывающий текст
        for (f in floaters) {
            t.style = Paint.Style.FILL
            t.color = f.color
            t.alpha = (f.life.coerceIn(0f, 1f) * 255).toInt()
            t.textSize = f.size
            t.textAlign = Paint.Align.CENTER
            t.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(f.text, f.x, f.y, t)
        }
        t.alpha = 255
    }

    private fun drawPolyline(c: Canvas, pts: List<PointF>) {
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        c.drawPath(path, p)
    }

    // =====================================================================
    //  UI: ЛАЙАУТ КНОПОК
    // =====================================================================

    private fun menuPlay(): RectF {
        val r = 86f * d
        return RectF(W / 2f - r, H * 0.56f - r, W / 2f + r, H * 0.56f + r)
    }

    private fun menuShop(): RectF {
        val w = 220f * d
        val h = 62f * d
        val cy = H * 0.56f + 130f * d
        return RectF(W / 2f - w / 2f, cy - h / 2f, W / 2f + w / 2f, cy + h / 2f)
    }

    private fun menuSound(): RectF = RectF(W - 68f * d, 20f * d, W - 20f * d, 68f * d)

    private fun hudPause(): RectF = RectF(W - 68f * d, 20f * d, W - 20f * d, 68f * d)

    private fun weaponBtn(i: Int): RectF {
        val barH = 104f * d
        val top = H - barH
        val size = 62f * d
        val gap = 14f * d
        val total = 5 * size + 4 * gap
        val x0 = (W - total) / 2f
        val left = x0 + i * (size + gap)
        val topBtn = top + (barH - size) / 2f
        return RectF(left, topBtn, left + size, topBtn + size)
    }

    private fun centerRect(cy: Float, w: Float, h: Float): RectF =
        RectF(W / 2f - w / 2f, cy - h / 2f, W / 2f + w / 2f, cy + h / 2f)

    private fun pauseResume(): RectF = centerRect(H * 0.46f, 250f * d, 68f * d)
    private fun pauseRestart(): RectF = centerRect(H * 0.55f, 250f * d, 68f * d)
    private fun pauseMenu(): RectF = centerRect(H * 0.64f, 250f * d, 68f * d)

    private fun overRestart(): RectF = centerRect(H * 0.52f, 250f * d, 68f * d)
    private fun overShop(): RectF = centerRect(H * 0.61f, 250f * d, 68f * d)
    private fun overMenu(): RectF = centerRect(H * 0.70f, 250f * d, 68f * d)

    private fun shopCardTop(i: Int): Float = H * 0.19f + i * (96f * d + 12f * d)
    private fun shopCard(i: Int): RectF {
        val top = shopCardTop(i)
        return RectF(20f * d, top, W - 20f * d, top + 96f * d)
    }
    private fun shopBuy(i: Int): RectF {
        val card = shopCard(i)
        val w = 108f * d
        return RectF(card.right - w - 16f * d, card.top + 22f * d, card.right - 16f * d, card.bottom - 22f * d)
    }
    private fun shopBack(): RectF = centerRect(H - 62f * d, 240f * d, 64f * d)

    // =====================================================================
    //  UI: ОТРИСОВКА
    // =====================================================================

    private fun fillRound(c: Canvas, r: RectF, radius: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.alpha = 255
        p.color = color
        c.drawRoundRect(r, radius, radius, p)
    }

    private fun strokeRound(c: Canvas, r: RectF, radius: Float, color: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = color
        c.drawRoundRect(r, radius, radius, p)
        p.style = Paint.Style.FILL
    }

    private fun label(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = true,
        align: Paint.Align = Paint.Align.CENTER
    ) {
        t.style = Paint.Style.FILL
        t.alpha = 255
        t.color = color
        t.textSize = size
        t.textAlign = align
        t.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(text, x, y, t)
    }

    private fun button(c: Canvas, r: RectF, text: String, bg: Int, fg: Int, size: Float = 26f) {
        fillRound(c, r, r.height() / 2.4f, bg)
        label(c, text, r.centerX(), r.centerY() + size * d * 0.35f, size * d, fg)
    }

    // ---------- меню ----------

    private fun drawMenu(c: Canvas) {
        // панель
        val panel = RectF(W * 0.08f, H * 0.20f, W * 0.92f, H * 0.86f)
        fillRound(c, panel, 34f * d, 0xF2FFFFFF.toInt())

        val bob = sin(time * 2.4f) * 6f * d
        label(c, "АБОБА", W / 2f, H * 0.31f + bob, 64f * d, INK)
        label(c, "РАЗРУШЕНИЕ", W / 2f, H * 0.365f + bob, 26f * d, ACCENT)

        // большая кнопка играть
        val play = menuPlay()
        fillRound(c, play, play.width() / 2f, ACCENT)
        p.color = 0x22000000
        c.drawCircle(play.centerX(), play.centerY() + 5f * d, play.width() / 2f, p)
        fillRound(c, play, play.width() / 2f, ACCENT)
        label(c, "ИГРАТЬ", play.centerX(), play.centerY() + 12f * d, 32f * d, 0xFFFFFFFF.toInt())

        button(c, menuShop(), "МАГАЗИН", YELLOW, INK, 24f)

        // рекорды
        label(
            c, "Рекорд: волна ${prefs.bestWave} · ${prefs.bestScore} очков",
            W / 2f, H * 0.80f, 20f * d, INK
        )
        label(
            c, "Монеты: $bank",
            W / 2f, H * 0.845f, 22f * d, ACCENT_DARK
        )

        // звук
        val snd = menuSound()
        fillRound(c, snd, 14f * d, if (prefs.sound) GREEN else 0xFFBDBDBD.toInt())
        label(c, if (prefs.sound) "♪" else "✕", snd.centerX(), snd.centerY() + 9f * d, 28f * d, 0xFFFFFFFF.toInt())
    }

    // ---------- HUD ----------

    private fun drawHud(c: Canvas) {
        // верхняя панель
        val bar = RectF(0f, 0f, W, 104f * d)
        fillRound(c, bar, 0f, 0xD9FFFFFF.toInt())

        label(c, "Очки: $score", 20f * d, 44f * d, 22f * d, INK, true, Paint.Align.LEFT)
        label(c, "Волна $wave", 20f * d, 76f * d, 20f * d, ACCENT_DARK, true, Paint.Align.LEFT)

        label(c, "$bank", W / 2f, 44f * d, 24f * d, 0xFFF9A825.toInt())
        label(c, "монет", W / 2f, 74f * d, 17f * d, INK, false)

        if (combo > 1) {
            label(c, "x$combo КОМБО", W / 2f + 90f * d, 96f * d, 18f * d, ACCENT)
        }

        // пауза
        val pause = hudPause()
        fillRound(c, pause, 16f * d, 0xFFEFEFEF.toInt())
        p.color = INK
        c.drawRoundRect(pause.left + 14f * d, pause.top + 14f * d, pause.left + 20f * d, pause.bottom - 14f * d, 3f * d, 3f * d, p)
        c.drawRoundRect(pause.right - 20f * d, pause.top + 14f * d, pause.right - 14f * d, pause.bottom - 14f * d, 3f * d, 3f * d, p)

        // панель оружия
        val barH = 104f * d
        fillRound(c, RectF(0f, H - barH, W, H), 0f, 0xFFFFF3D6.toInt())

        for (i in 0..4) {
            val r = weaponBtn(i)
            val selected = weapon == i
            val ready = i == 0 || ammo[i - 1] >= 1f
            fillRound(c, r, r.width() / 2f, if (ready) 0xFFFFFFFF.toInt() else 0xFFE0E0E0.toInt())
            if (selected) strokeRound(c, r, r.width() / 2f, ACCENT, 5f * d)
            drawWeaponIcon(c, r.centerX(), r.centerY(), r.width() * 0.30f, i, if (ready) INK else 0xFFAAAAAA.toInt())

            if (i > 0) {
                val a = ammo[i - 1].toInt()
                val badge = RectF(r.right - 26f * d, r.top - 6f * d, r.right + 2f * d, r.top + 22f * d)
                fillRound(c, badge, 10f * d, if (a > 0) ACCENT else 0xFF9E9E9E.toInt())
                label(c, "$a", badge.centerX(), badge.centerY() + 6f * d, 15f * d, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun drawWeaponIcon(c: Canvas, cx: Float, cy: Float, r: Float, index: Int, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        when (index) {
            0 -> { // взрыв
                for (i in 0 until 8) {
                    val ang = i * Math.PI.toFloat() / 4f
                    c.drawCircle(
                        cx + kotlin.math.cos(ang) * r * 1.25f,
                        cy + kotlin.math.sin(ang) * r * 1.25f,
                        r * 0.42f, p
                    )
                }
                c.drawCircle(cx, cy, r * 0.85f, p)
            }
            1 -> { // молния
                val path = Path()
                path.moveTo(cx + r * 0.25f, cy - r * 1.3f)
                path.lineTo(cx - r * 0.7f, cy + r * 0.15f)
                path.lineTo(cx - r * 0.05f, cy + r * 0.15f)
                path.lineTo(cx - r * 0.35f, cy + r * 1.3f)
                path.lineTo(cx + r * 0.75f, cy - r * 0.2f)
                path.lineTo(cx + r * 0.1f, cy - r * 0.2f)
                path.close()
                c.drawPath(path, p)
            }
            2 -> { // метеор
                c.drawCircle(cx + r * 0.5f, cy + r * 0.5f, r * 0.8f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.32f
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx + r * 0.4f, cy + r * 0.4f, cx - r * 1.1f, cy - r * 1.1f, p)
                c.drawLine(cx + r * 0.1f, cy + r * 0.7f, cx - r * 0.9f, cy - r * 0.3f, p)
                p.style = Paint.Style.FILL
            }
            3 -> { // чёрная дыра
                c.drawCircle(cx, cy, r * 0.65f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.25f
                c.drawCircle(cx, cy, r * 1.15f, p)
                p.style = Paint.Style.FILL
            }
            else -> { // ядерка
                c.drawCircle(cx, cy, r * 0.35f, p)
                for (i in 0 until 3) {
                    val ang = i * 2.0944f - 1.5708f
                    val x = cx + kotlin.math.cos(ang) * r * 1.15f
                    val y = cy + kotlin.math.sin(ang) * r * 1.15f
                    c.drawCircle(x, y, r * 0.42f, p)
                }
            }
        }
    }

    // ---------- пауза ----------

    private fun drawPauseOverlay(c: Canvas) {
        p.color = 0x99000000.toInt()
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, W, H, p)

        button(c, pauseResume(), "ПРОДОЛЖИТЬ", GREEN, 0xFFFFFFFF.toInt(), 24f)
        button(c, pauseRestart(), "ЗАНОВО", ACCENT, 0xFFFFFFFF.toInt(), 24f)
        button(c, pauseMenu(), "В МЕНЮ", 0xFFFFFFFF.toInt(), INK, 24f)
    }

    // ---------- конец игры ----------

    private fun drawGameOverOverlay(c: Canvas) {
        p.color = 0xAA000000.toInt()
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, W, H, p)

        label(c, "АБОБА ПАЛА", W / 2f, H * 0.26f, 44f * d, 0xFFFFD54F.toInt())
        label(c, "Волна $wave", W / 2f, H * 0.33f, 28f * d, 0xFFFFFFFF.toInt())
        label(c, "Очки: $score", W / 2f, H * 0.375f, 26f * d, 0xFFFFFFFF.toInt())
        label(c, "Убито: $kills", W / 2f, H * 0.415f, 22f * d, 0xFFFFFFFF.toInt())
        label(c, "Монет заработано: $bank", W / 2f, H * 0.455f, 20f * d, 0xFFFFD54F.toInt())

        button(c, overRestart(), "ЕЩЁ РАЗ", GREEN, 0xFFFFFFFF.toInt(), 24f)
        button(c, overShop(), "МАГАЗИН", YELLOW, INK, 24f)
        button(c, overMenu(), "В МЕНЮ", 0xFFFFFFFF.toInt(), INK, 24f)
    }

    // ---------- магазин ----------

    private fun drawShop(c: Canvas) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, H, 0xFFFFE082.toInt(), 0xFFFFCC80.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        label(c, "МАГАЗИН", W / 2f, H * 0.10f, 40f * d, INK)
        label(c, "Монеты: $bank", W / 2f, H * 0.145f, 24f * d, ACCENT_DARK)

        val names = arrayOf("Радиус взрыва", "Урон", "Боезапас", "Перезарядка")
        val hints = arrayOf("+9% радиус", "+1 урон", "+1 заряд", "+ускорение")

        for (i in 0..3) {
            val card = shopCard(i)
            fillRound(c, card, 20f * d, CARD)

            val level = prefs.upgrade(i)
            label(
                c, names[i],
                card.left + 24f * d, card.top + 38f * d,
                22f * d, INK, true, Paint.Align.LEFT
            )
            label(
                c, "${hints[i]} · Ур. $level/8",
                card.left + 24f * d, card.top + 68f * d,
                17f * d, 0xFF757575.toInt(), false, Paint.Align.LEFT
            )

            val buy = shopBuy(i)
            val maxed = level >= 8
            val cost = upgradeCost(level)
            val canBuy = !maxed && bank >= cost

            fillRound(
                c, buy, 14f * d,
                when {
                    maxed -> 0xFFBDBDBD.toInt()
                    canBuy -> GREEN
                    else -> 0xFFE57373.toInt()
                }
            )
            if (maxed) {
                label(c, "МАКС", buy.centerX(), buy.centerY() + 8f * d, 18f * d, 0xFFFFFFFF.toInt())
            } else {
                label(c, "$cost", buy.centerX(), buy.centerY() + 2f * d, 20f * d, 0xFFFFFFFF.toInt())
                label(c, "монет", buy.centerX(), buy.centerY() + 22f * d, 13f * d, 0xFFFFFFFF.toInt(), false)
            }
        }

        button(c, shopBack(), "НАЗАД", 0xFFFFFFFF.toInt(), INK, 24f)
    }
}
