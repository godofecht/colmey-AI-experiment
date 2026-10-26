package ai.colmey.instrument

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Specialist five-class ViT. Model and processor metadata are bundled by CI;
 * never silently substitute the generic image labeler for a missing model.
 */
data class InstrumentScore(val label: String, val probability: Float)
data class VisionResult(val scores: List<InstrumentScore>, val elapsedMs: Long, val imageWidth: Int, val imageHeight: Int)

class InstrumentVision(private val context: Context) {
    fun identify(uri: Uri): VisionResult {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Unable to decode image")
        return try { identify(original) } finally { original.recycle() }
    }

    fun identify(bitmap: Bitmap): VisionResult {
        val started = android.os.SystemClock.elapsedRealtime()
        val metadata = context.assets.open("instrument_preprocessor.json").bufferedReader().use { JSONObject(it.readText()) }
        val config = context.assets.open("instrument_config.json").bufferedReader().use { JSONObject(it.readText()) }
        val sizeObject = metadata.optJSONObject("size")
        val height = sizeObject?.optInt("height", 224) ?: 224
        val width = sizeObject?.optInt("width", 224) ?: 224
        require(width in 1..512 && height in 1..512)
        val mean = metadata.optJSONArray("image_mean")
        val std = metadata.optJSONArray("image_std")
        val rescale = metadata.optDouble("rescale_factor", 1.0 / 255.0).toFloat()
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val plane = width * height
        val input = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val channels = intArrayOf(p shr 16 and 255, p shr 8 and 255, p and 255)
            for (channel in 0..2) {
                val m = mean?.optDouble(channel, 0.5)?.toFloat() ?: 0.5f
                val s = std?.optDouble(channel, 0.5)?.toFloat() ?: 0.5f
                input[channel * plane + i] = (channels[channel] * rescale - m) / s
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        val modelFile = java.io.File(context.filesDir, "instrument.onnx")
        if (!modelFile.exists()) {
            context.assets.open("instrument.onnx").use { source ->
                modelFile.outputStream().use { source.copyTo(it) }
            }
        }
        val env = OrtEnvironment.getEnvironment()
        env.createSession(modelFile.absolutePath, OrtSession.SessionOptions()).use { session ->
            val name = session.inputNames.single()
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, height.toLong(), width.toLong())).use { tensor ->
                session.run(mapOf(name to tensor)).use { result ->
                    val output = (result[0].value as Array<*>)[0] as FloatArray
                    require(output.isNotEmpty() && output.all { it.isFinite() }) { "Invalid model output" }
                    val maximum = output.maxOrNull() ?: error("Empty model output")
                    val exponents = output.map { exp((it - maximum).toDouble()) }
                    val denominator = exponents.sum()
                    val labels = config.getJSONObject("id2label")
                    require(output.size == labels.length()) { "Output label count mismatch" }
                    val scores = output.indices.map { index ->
                        InstrumentScore(labels.getString(index.toString()), (exponents[index] / denominator).toFloat())
                    }.sortedByDescending { it.probability }
                    return VisionResult(scores, android.os.SystemClock.elapsedRealtime() - started, bitmap.width, bitmap.height)
                }
            }
        }
    }
}
