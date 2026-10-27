package ai.colmey.instrument

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentAppSmokeTest {
    @Test fun browseOfflineCatalogAndShowOrigin() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Browse instruments (offline)")).perform(click())
            onView(withText("Piano")).perform(click())
            onView(withText(org.hamcrest.Matchers.containsString("Region: Italy")))
                .check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
        }
    }

    @Test fun bundledSpecialistModelRunsRealImageInference() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        try {
            // A blank image need not be recognised; this checks that the specialist
            // ONNX model loads, preprocesses pixels and returns without exceptions.
            val inference = InstrumentVision(context).identify(bitmap)
            org.junit.Assert.assertEquals(55, inference.scores.size)
            org.junit.Assert.assertEquals(1.0, inference.scores.sumOf { it.probability.toDouble() }, 0.002)
            org.junit.Assert.assertTrue(inference.scores.zipWithNext().all { it.first.probability >= it.second.probability })
        } finally {
            bitmap.recycle()
        }
    }
    @Test fun realInstrumentPhotographsProduceRankedPredictions() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val labels = listOf("guitar", "violin", "piano")
        var topOneCorrect = 0
        var topFiveCorrect = 0
        for (expected in labels) {
            val bitmap = instrumentation.context.assets.open("instrument_photos/$expected.jpg").use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw AssertionError("Cannot decode real photo fixture: $expected")
            try {
                val prediction = InstrumentVision(context).identify(bitmap)
                val ranked = prediction.scores.map { it.label }
                val rank = ranked.indexOf(expected) + 1
                val confidence = prediction.scores.firstOrNull { it.label == expected }?.probability ?: 0f
                android.util.Log.i("ColmeyVisionBenchmark",
                    "actual=$expected rank=$rank top1=${ranked.firstOrNull()} expectedScore=$confidence elapsedMs=${prediction.elapsedMs}")
                Assert.assertEquals("All 55 classes must be scored for $expected", 55, ranked.size)
                Assert.assertEquals("Scores should sum to 1 for $expected",
                    1.0, prediction.scores.sumOf { it.probability.toDouble() }, 0.002)
                Assert.assertTrue("No invalid scores for $expected",
                    prediction.scores.all { it.probability.isFinite() && it.probability >= 0f })
                if (rank == 1) topOneCorrect++
                if (rank in 1..5) topFiveCorrect++
            } finally {
                bitmap.recycle()
            }
        }
        android.util.Log.i("ColmeyVisionBenchmark",
            "Real photo benchmark: top1=$topOneCorrect/${labels.size}, top5=$topFiveCorrect/${labels.size}")
        Assert.assertTrue("Model must recognize at least 2 of 3 real instrument photos in its top five",
            topFiveCorrect >= 2)
    }

}
