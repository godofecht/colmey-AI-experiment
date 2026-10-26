package ai.colmey.instrument

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

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

    @Test fun bundledMlKitModelCanProcessAnImage() {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val labels = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
            assertNotNull(labels)
        } finally {
            labeler.close()
            bitmap.recycle()
        }
    }
}
