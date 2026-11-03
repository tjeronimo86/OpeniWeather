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

/**
 * Eigene Bilder der Wolkenschleife. Das Widget blendet jedes weich ins naechste
 * (res/anim/sky_in.xml), deshalb wirkt die Bewegung trotz weniger Bilder fluessig.
 * Bei 450 ms pro Bild dauert die Schleife rund 6,8 Sekunden.
 */
const val SKY_FRAMES = 14

/** Wolkenzeit pro Schleife: so weit ziehen die Wolken, bevor die Schleife sich schliesst. */
private const val LOOP = 20f

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
     * Eine nahtlose Wolkenschleife, die Wolken ziehen dabei immer in dieselbe Richtung.
     * Der Shader mischt jeden Zeitpunkt mit demselben Zeitpunkt eine Schleife frueher
     * (uLoopW), vor der Wolkenkante: Jedes Bild bleibt scharf und das letzte fuehrt genau
     * auf das erste zu. Das erste Bild steht am Ende noch einmal, damit auch dieser
     * Uebergang weich eingeblendet wird; dieselbe Bitmap kostet keinen Speicher.
     */
    fun clouds(context: Context, width: Int, height: Int, p: SkyParams): List<Bitmap> = runCatching {
        Session(context, width, height, p).use { session ->
            val frames = List(SKY_FRAMES) { i ->
                val w = i / SKY_FRAMES.toFloat()
                session.frame(41f + w * LOOP, w)
            }
            frames + frames.first()
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
            // Sterne und Funkeln stehen im Widget still, sonst springen sie am Schleifenende
            shader.setFloatUniform("uTime", 41f)
            shader.setFloatUniform("uLoopT", LOOP)
            node.setPosition(0, 0, width, height)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
        }

        fun frame(cloudTime: Float, loopW: Float): Bitmap {
            shader.setFloatUniform("uCloudTime", cloudTime)
            shader.setFloatUniform("uLoopW", loopW)
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
