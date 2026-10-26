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
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.ProgressBar
import android.view.View
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var result: TextView
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var scoresContainer: LinearLayout
    private var captureUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
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
        status = TextView(this).apply { text = "Ready · MobileCLIP2 offline model"; textSize = 15f }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 720
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Photograph being analyzed"
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        scoresContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(title)
        layout.addView(status)
        layout.addView(preview)
        layout.addView(progress)
        layout.addView(camera)
        layout.addView(gallery)
        val browse = Button(this).apply {
            text = "Browse instruments (offline)"
            setOnClickListener { showInstrumentPicker() }
        }
        layout.addView(browse)
        layout.addView(result)
        layout.addView(scoresContainer)
        captureUri = savedInstanceState?.getString("capture_uri")?.let(Uri::parse)
        scroll.addView(layout)
        setContentView(scroll)
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
        status.text = "Step 1/3 · Loading photo"
        progress.visibility = View.VISIBLE
        scoresContainer.removeAllViews()
        preview.setImageURI(uri)
        result.text = "Running specialist offline model…"
        Thread {
            try {
                runOnUiThread { status.text = "Step 2/3 · Preprocessing and running ONNX model" }
                val recognition = InstrumentVision(applicationContext).identify(uri)
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Step 3/3 · Completed in ${recognition.elapsedMs} ms · ${recognition.imageWidth} × ${recognition.imageHeight} pixels"
                    showScores(recognition)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Inference failed"
                    result.text = "Model error: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun showScores(recognition: VisionResult) {
        val top = recognition.scores.firstOrNull()
        val match = top?.let { InstrumentCatalog.match(it.label) }
        val accepted = top != null && top.probability >= 0.20f
        result.text = if (accepted && top != null) {
            "${top.label.replaceFirstChar { it.titlecase(Locale.ROOT) }} — ${"%.1f".format(Locale.ROOT, top.probability * 100)}% relative model score\n${match?.second?.history ?: "No curated history available yet."}\nRegion: ${match?.second?.region ?: "Not yet catalogued"}"
        } else {
            "Uncertain identification. These are relative scores across the model's instrument descriptions."
        }
        scoresContainer.addView(TextView(this).apply {
            text = "Top 10 instrument matches"
            textSize = 19f
            setPadding(0, 24, 0, 12)
        })
        recognition.scores.take(10).forEach { prediction ->
            val name = prediction.label.replaceFirstChar { it.titlecase(Locale.ROOT) }
            scoresContainer.addView(TextView(this).apply {
                text = "$name — ${"%.1f".format(Locale.ROOT, prediction.probability * 100)}%"
                textSize = 16f
            })
            scoresContainer.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (prediction.probability * 1000).toInt()
                contentDescription = "$name confidence ${(prediction.probability * 100).toInt()} percent"
            })
        }
        scoresContainer.addView(TextView(this).apply {
            text = "Model: MobileCLIP2-S0 (ONNX) · offline. Scores are relative similarities across 55 instruments, not calibrated certainty."
            setPadding(0, 18, 0, 20)
        })
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
