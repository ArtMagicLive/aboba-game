package com.aboba.destruction.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Синтезированные звуки: генерируем короткие WAV-файлы в кэш и грузим в SoundPool.
 * Никаких бинарных ресурсов в проекте — всё считается кодом.
 */
class SoundFx(private val context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    var enabled: Boolean = true

    private var boomId = 0
    private var popId = 0
    private var coinId = 0
    private var zapId = 0
    private var clickId = 0
    private var hurtId = 0

    private val sampleRate = 22050

    init {
        try {
            boomId = load("boom", genBoom())
            popId = load("pop", genPop())
            coinId = load("coin", genCoin())
            zapId = load("zap", genZap())
            clickId = load("click", genClick())
            hurtId = load("hurt", genHurt())
        } catch (t: Throwable) {
            enabled = false
        }
    }

    // ---------- воспроизведение ----------

    fun playBoom() = play(boomId, 1f)
    fun playPop() = play(popId, 0.7f)
    fun playCoin() = play(coinId, 0.8f)
    fun playZap() = play(zapId, 0.8f)
    fun playClick() = play(clickId, 0.6f)
    fun playHurt() = play(hurtId, 0.9f)

    private fun play(id: Int, volume: Float) {
        if (!enabled || id == 0) return
        try {
            pool.play(id, volume, volume, 1, 0, 1f)
        } catch (t: Throwable) {
            // игнорируем
        }
    }

    // ---------- загрузка ----------

    private fun load(name: String, data: ShortArray): Int {
        val file = File(context.cacheDir, "$name.wav")
        writeWav(file, data)
        return pool.load(file.absolutePath, 1)
    }

    private fun writeWav(file: File, data: ShortArray) {
        val bytes = ByteArray(44 + data.size * 2)

        fun put32(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun put16(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        put32(4, 36 + data.size * 2)

        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()

        bytes[12] = 'f'.code.toByte()
        bytes[13] = 'm'.code.toByte()
        bytes[14] = 't'.code.toByte()
        bytes[15] = ' '.code.toByte()
        put32(16, 16)
        put16(20, 1)              // PCM
        put16(22, 1)              // mono
        put32(24, sampleRate)
        put32(28, sampleRate * 2) // byte rate
        put16(32, 2)              // block align
        put16(34, 16)             // bits per sample

        bytes[36] = 'd'.code.toByte()
        bytes[37] = 'a'.code.toByte()
        bytes[38] = 't'.code.toByte()
        bytes[39] = 'a'.code.toByte()
        put32(40, data.size * 2)

        for (i in data.indices) put16(44 + i * 2, data[i].toInt())

        file.writeBytes(bytes)
    }

    // ---------- генераторы ----------

    private fun genBoom(): ShortArray {
        val n = (sampleRate * 0.55f).toInt()
        val out = ShortArray(n)
        var lowPass = 0f
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 7.0)
            val noise = Random.nextFloat() * 2f - 1f
            lowPass += (noise - lowPass) * 0.12f
            val tone = sin(2.0 * PI * (95.0 - t * 130.0) * t)
            val v = (lowPass * 0.75f + tone.toFloat() * 0.55f) * env.toFloat()
            out[i] = (v * 24000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun genPop(): ShortArray {
        val n = (sampleRate * 0.14f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val freq = 720.0 - t * 3600.0
            val env = exp(-t * 22.0)
            val v = sin(2.0 * PI * freq * t) * env
            out[i] = (v * 20000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun genCoin(): ShortArray {
        val n = (sampleRate * 0.18f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val freq = if (t < 0.06) 1050.0 else 1560.0
            val env = exp(-t * 11.0)
            val v = sin(2.0 * PI * freq * t) * env
            out[i] = (v * 15000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun genZap(): ShortArray {
        val n = (sampleRate * 0.28f).toInt()
        val out = ShortArray(n)
        var prev = 0f
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 12.0)
            val noise = Random.nextFloat() * 2f - 1f
            val high = noise - prev
            prev = noise
            val v = high * env.toFloat()
            out[i] = (v * 17000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun genClick(): ShortArray {
        val n = (sampleRate * 0.05f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 90.0)
            val v = sin(2.0 * PI * 1200.0 * t) * env
            out[i] = (v * 12000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun genHurt(): ShortArray {
        val n = (sampleRate * 0.30f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val freq = 420.0 - t * 900.0
            val env = exp(-t * 6.0)
            val v = sin(2.0 * PI * freq * t) * env * 0.7 + (Random.nextFloat() * 2f - 1f) * 0.15f * env.toFloat()
            out[i] = (v * 22000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
