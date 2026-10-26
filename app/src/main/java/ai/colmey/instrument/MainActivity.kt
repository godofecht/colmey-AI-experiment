package ai.colmey.instrument

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var result: TextView
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 64, 36, 32)
        }
        val title = TextView(this).apply {
            text = "Colmey AI · Instrument Explorer"
            textSize = 23f
        }
        result = TextView(this).apply {
            text = "Photograph an instrument to attempt offline identification. Results are suggestions, not definitive provenance."
            textSize = 18f
            setPadding(0, 32, 0, 32)
        }
        val camera = Button(this).apply {
            text = "Take photo"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 10)
            }
        }
        val gallery = Button(this).apply {
            text = "Choose photo"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 11)
            }
        }
        layout.addView(title)
        layout.addView(camera)
        layout.addView(gallery)
        layout.addView(result)
        setContentView(layout)
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val image = try {
            when (requestCode) {
                10 -> {
                    @Suppress("DEPRECATION")
                    val bitmap = data.extras?.get("data") as? Bitmap ?: return
                    InputImage.fromBitmap(bitmap, 0)
                }
                11 -> InputImage.fromFilePath(this, data.data ?: return)
                else -> return
            }
        } catch (e: Exception) {
            result.text = "Could not open image: ${e.localizedMessage}"
            return
        }
        result.text = "Identifying locally…"
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val match = labels.mapNotNull { label ->
                    val name = label.text.lowercase(Locale.ROOT)
                    val entry = InstrumentCatalog.match(name)
                    if (entry != null) Triple(entry.first, entry.second, label.confidence) else null
                }.maxByOrNull { it.third }
                result.text = if (match == null || match.third < 0.50f) {
                    "Instrument not confidently identified. The bundled general-purpose classifier cannot recognise many regional or rare instruments. Try a clear, close photograph."
                } else {
                    "${match.first.replaceFirstChar { it.titlecase(Locale.ROOT) }}\nConfidence: ${(match.third * 100).toInt()}% (general image label)\n\n${match.second.history}\nRegion: ${match.second.region}\n\nThis describes the instrument family's history, not the provenance of the photographed object."
                }
            }
            .addOnFailureListener { e -> result.text = "Local recognition failed: ${e.localizedMessage}" }
    }

    override fun onDestroy() {
        labeler.close()
        super.onDestroy()
    }
}
