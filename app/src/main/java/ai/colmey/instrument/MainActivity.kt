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

    private val origins = mapOf(
        "guitar" to "Guitar family: developed in Europe, with earlier ancestors across the Mediterranean and Middle East.",
        "violin" to "Violin: developed in northern Italy during the 16th century.",
        "piano" to "Piano: invented in Italy around 1700 by Bartolomeo Cristofori.",
        "saxophone" to "Saxophone: invented by Adolphe Sax in Belgium in the 1840s.",
        "trumpet" to "Trumpet: ancient predecessors across multiple regions; modern valved trumpet developed in Europe.",
        "flute" to "Flutes: independently developed in many regions since prehistory.",
        "drum" to "Drums: developed independently across many cultures; no single geographic origin.",
        "banjo" to "Banjo: developed by African-descended communities in the Caribbean and North America.",
        "sitar" to "Sitar: developed in the Indian subcontinent.",
        "tabla" to "Tabla: developed in the Indian subcontinent.",
        "harp" to "Harps: ancient instruments documented in Africa and Western Asia.",
        "clarinet" to "Clarinet: developed in Germany around 1700.",
        "accordion" to "Accordion: developed in Europe in the early 19th century.",
        "ukulele" to "Ukulele: developed in Hawaii from Portuguese-introduced instruments.",
        "cello" to "Cello: developed in Italy during the 16th century.",
        "organ" to "Pipe organ: roots in ancient Greek hydraulis technology.",
        "keyboard" to "Keyboard instruments have varied histories; a specific type is needed.",
        "microphone" to "This is a microphone, not a musical instrument."
    )

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
                    val entry = origins.entries.firstOrNull { name.contains(it.key) || it.key.contains(name) && name.length > 3 }
                    if (entry != null) Triple(entry.key, entry.value, label.confidence) else null
                }.maxByOrNull { it.third }
                result.text = if (match == null) {
                    "Instrument not confidently identified. The bundled general-purpose classifier cannot recognise many regional or rare instruments. Try a clear, close photograph."
                } else {
                    "${match.first.replaceFirstChar { it.titlecase(Locale.ROOT) }}\nConfidence: ${(match.third * 100).toInt()}% (general image label)\n\n${match.second}\n\nThis describes the instrument family's history, not the provenance of the photographed object."
                }
            }
            .addOnFailureListener { e -> result.text = "Local recognition failed: ${e.localizedMessage}" }
    }

    override fun onDestroy() {
        labeler.close()
        super.onDestroy()
    }
}
