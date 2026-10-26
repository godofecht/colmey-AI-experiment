package ai.colmey.instrument

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File
import android.net.Uri
import android.provider.MediaStore
import android.view.Gravity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var result: TextView
    private var captureUri: Uri? = null

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
                val directory = File(cacheDir, "camera").apply { mkdirs() }
                val photo = File.createTempFile("instrument-", ".jpg", directory)
                captureUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", photo)
                startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }, 10)
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
        val browse = Button(this).apply {
            text = "Browse instruments (offline)"
            setOnClickListener { showInstrumentPicker() }
        }
        layout.addView(browse)
        layout.addView(result)
        captureUri = savedInstanceState?.getString("capture_uri")?.let(Uri::parse)
        setContentView(layout)
        checkForUpdates()
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = when (requestCode) {
            10 -> captureUri
            11 -> data?.data
            else -> null
        } ?: return
        result.text = "Identifying instrument offline…"
        Thread {
            val recognition = try {
                InstrumentVision(applicationContext).identify(uri)
            } catch (e: Exception) {
                runOnUiThread {
                    result.text = "Specialist model unavailable or inference failed: ${e.localizedMessage}"
                }
                return@Thread
            }
            runOnUiThread {
                result.text = if (recognition == null) {
                    "Instrument not confidently identified by the specialist five-class model. Try another photo or browse the catalog."
                } else {
                    "${recognition.name.replaceFirstChar { it.titlecase(Locale.ROOT) }}\\nConfidence: ${(recognition.confidence * 100).toInt()}% (specialist model)\\n\\n${recognition.origin.history}\\nRegion: ${recognition.origin.region}"
                }
            }
        }.start()
    }

    private fun checkForUpdates() {
        Thread {
            val update = try {
                UpdateChecker.fetch(packageManager.getPackageInfo(packageName, 0).versionCode)
            } catch (_: Exception) {
                null
            }
            if (update != null) {
                Handler(Looper.getMainLooper()).post {
                    if (!isFinishing && !isDestroyed) {
                        AlertDialog.Builder(this)
                            .setTitle("Colmey AI update available")
                            .setMessage("Build ${update.build} is available. Open the verified GitHub release to download it? Android will ask you to confirm installation.")
                            .setPositiveButton("Download") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    private fun showInstrumentPicker() {
        val names = InstrumentCatalog.names()
        AlertDialog.Builder(this)
            .setTitle("Choose an instrument")
            .setItems(names.map { it.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }.toTypedArray()) { _, index ->
                val name = names[index]
                val origin = InstrumentCatalog.origin(name) ?: return@setItems
                result.text = "${name.replaceFirstChar { it.titlecase(Locale.ROOT) }}\nRegion: ${origin.region}\n\n${origin.history}\n\nSelected manually; not identified from the photograph."
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureUri?.let { outState.putString("capture_uri", it.toString()) }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
