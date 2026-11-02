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
 * Rendert den App-Shader einmalig in ein Bitmap, damit Widgets denselben Himmel zeigen.
 * RuntimeShader laeuft nur auf der GPU, deshalb der Umweg ueber HardwareRenderer.
 * Gibt null zurueck, wenn es nicht klappt; das Widget nimmt dann einen Verlauf.
 */
object SkyBitmap {
    fun render(context: Context, width: Int, height: Int, p: SkyParams): Bitmap? = runCatching {
        val source = context.assets.open("shaders/sky.agsl").bufferedReader().use { it.readText() }
        val shader = RuntimeShader(source)
        shader.setFloatUniform("uRes", width.toFloat(), height.toFloat())
        shader.setFloatUniform("uTime", 41f)
        shader.setFloatUniform("uCloud", p.cloud)
        shader.setFloatUniform("uGrey", p.grey)
        shader.setFloatUniform("uRain", p.rain)
        shader.setFloatUniform("uSnow", p.snow)
        shader.setFloatUniform("uFog", p.fog)
        // Ein Standbild soll nicht zufaellig mitten im Blitz haengen
        shader.setFloatUniform("uStorm", 0f)
        shader.setFloatUniform("uDay", p.day)
        shader.setFloatUniform("uDusk", p.dusk)
        shader.setFloatUniform("uSun", p.sunX, p.sunY)
        shader.setFloatUniform("uMoon", p.moon)

        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            val node = RenderNode("sky")
            node.setPosition(0, 0, width, height)
            val canvas = node.beginRecording()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = requireNotNull(reader.acquireLatestImage())
            image.use {
                val buffer = requireNotNull(it.hardwareBuffer)
                buffer.use { hb ->
                    val wrapped = requireNotNull(Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB)))
                    wrapped.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            renderer.destroy()
            reader.close()
        }
    }.getOrNull()
}
