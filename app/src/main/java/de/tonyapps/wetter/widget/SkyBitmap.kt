package de.tonyapps.wetter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import de.tonyapps.wetter.sky.SkyParams
import kotlin.math.PI
import kotlin.math.cos

/** Eigene Bilder der Wolkenschleife; hin und zurueck ergibt das rund 4 Sekunden. */
const val SKY_FRAMES = 10

/** Wegstrecke der Wolken in Shader-Sekunden. */
private const val CLOUD_TRAVEL = 4f

/**
 * Rendert den App-Shader in Bitmaps, damit Widgets denselben Himmel zeigen.
 * RuntimeShader laeuft nur auf der GPU, deshalb der Umweg ueber HardwareRenderer.
 */
object SkyBitmap {
    /** Ein Standbild. Null, wenn es nicht klappt; das Widget nimmt dann einen Verlauf. */
    fun render(context: Context, width: Int, height: Int, p: SkyParams): Bitmap? = runCatching {
        Session(context, width, height, p).use { it.frame(41f, 41f) }
    }.getOrNull()

    /**
     * Eine nahtlose Wolkenschleife ohne Ueberblenden, jedes Bild bleibt scharf: Die Wolken
     * ziehen langsam hin und zurueck und werden an den Wendepunkten sanft abgebremst.
     * Fuer den Rueckweg werden dieselben Bitmaps wiederverwendet, das kostet keinen Speicher.
     */
    fun clouds(context: Context, width: Int, height: Int, p: SkyParams): List<Bitmap> = runCatching {
        Session(context, width, height, p).use { session ->
            val forward = List(SKY_FRAMES) { i ->
                val phase = i / (SKY_FRAMES - 1f)
                val eased = (1f - cos(phase * PI.toFloat())) / 2f
                session.frame(41f + i * .25f, 41f + eased * CLOUD_TRAVEL)
            }
            forward + forward.reversed().drop(1).dropLast(1)
        }
    }.getOrDefault(emptyList())

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
            // Niederschlag und Blitze sind im Widget eine eigene Ebene
            shader.setFloatUniform("uRain", 0f)
            shader.setFloatUniform("uSnow", 0f)
            shader.setFloatUniform("uStorm", 0f)
            shader.setFloatUniform("uSunVis", 1f)
            shader.setFloatUniform("uFog", p.fog)
            shader.setFloatUniform("uDay", p.day)
            shader.setFloatUniform("uDusk", p.dusk)
            shader.setFloatUniform("uSun", p.sunX, p.sunY)
            shader.setFloatUniform("uMoon", p.moon)
            node.setPosition(0, 0, width, height)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
        }

        fun frame(time: Float, cloudTime: Float): Bitmap {
            shader.setFloatUniform("uTime", time)
            shader.setFloatUniform("uCloudTime", cloudTime)
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
