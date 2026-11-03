package de.tonyapps.wetter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import de.tonyapps.wetter.sky.SkyParams

const val SKY_FRAMES = 20
private const val STEP = 0.1f

/** Wolken ziehen im Widget schneller, sonst saehe man in 2 Sekunden Schleife nichts. */
private const val CLOUD_SPEED = 7f

/**
 * Rendert den App-Shader in Bitmaps, damit Widgets denselben Himmel zeigen.
 * RuntimeShader laeuft nur auf der GPU, deshalb der Umweg ueber HardwareRenderer.
 */
object SkyBitmap {
    /** Ein Standbild. Null, wenn es nicht klappt; das Widget nimmt dann einen Verlauf. */
    fun render(context: Context, width: Int, height: Int, p: SkyParams): Bitmap? = runCatching {
        Session(context, width, height, p).use { it.frame(41f, 0f) }
    }.getOrNull()

    /**
     * Eine nahtlose Schleife. Der Himmel laeuft nicht von selbst im Kreis, also wird
     * ueberblendet: Jedes Bild mischt seinen Zeitpunkt mit demselben Zeitpunkt eine
     * Schleifenlaenge frueher. Das letzte Bild landet so genau vor dem ersten.
     */
    fun loop(context: Context, width: Int, height: Int, p: SkyParams): List<Bitmap> = runCatching {
        Session(context, width, height, p).use { session ->
            val start = 41f
            val frames = List(SKY_FRAMES) { i ->
                val time = start + i * STEP
                val now = session.frame(time, 0f)
                val earlier = session.frame(time - SKY_FRAMES * STEP, 0f)
                val mixed = now.copy(Bitmap.Config.ARGB_8888, true)
                val blend = Paint().apply { alpha = 255 * i / SKY_FRAMES }
                Canvas(mixed).drawBitmap(earlier, 0f, 0f, blend)
                now.recycle()
                earlier.recycle()
                mixed
            }
            if (p.storm > .5f) lightning(session, frames) else frames
        }
    }.getOrDefault(emptyList())

    /** Drei Bilder mit Blitz ersetzen eine Stelle der Schleife; der Shader blitzt bei t = 7,87. */
    private fun lightning(session: Session, frames: List<Bitmap>): List<Bitmap> {
        val out = frames.toMutableList()
        listOf(7.87f, 7.97f, 8.1f).forEachIndexed { i, time -> out[12 + i] = session.frame(time, 1f) }
        return out
    }

    private class Session(context: Context, val width: Int, val height: Int, val p: SkyParams) : AutoCloseable {
        private val shader = RuntimeShader(context.assets.open("shaders/sky.agsl").bufferedReader().use { it.readText() })
        private val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        private val renderer = HardwareRenderer()
        private val node = RenderNode("sky")
        private val paint = Paint()

        init {
            shader.setFloatUniform("uRes", width.toFloat(), height.toFloat())
            shader.setFloatUniform("uCloud", p.cloud)
            shader.setFloatUniform("uGrey", p.grey)
            shader.setFloatUniform("uRain", p.rain)
            shader.setFloatUniform("uSnow", p.snow)
            shader.setFloatUniform("uFog", p.fog)
            shader.setFloatUniform("uDay", p.day)
            shader.setFloatUniform("uDusk", p.dusk)
            shader.setFloatUniform("uSun", p.sunX, p.sunY)
            shader.setFloatUniform("uMoon", p.moon)
            node.setPosition(0, 0, width, height)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
        }

        fun frame(time: Float, storm: Float): Bitmap {
            shader.setFloatUniform("uTime", time)
            shader.setFloatUniform("uCloudTime", 41f + (time - 41f) * CLOUD_SPEED)
            shader.setFloatUniform("uStorm", storm)
            paint.shader = shader
            val canvas = node.beginRecording()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            node.endRecording()
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = requireNotNull(reader.acquireLatestImage())
            return image.use {
                requireNotNull(it.hardwareBuffer).use { buffer ->
                    requireNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)))
                        .copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        }

        override fun close() {
            renderer.destroy()
            reader.close()
        }
    }
}
