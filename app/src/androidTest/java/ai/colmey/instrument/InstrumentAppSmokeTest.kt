package ai.colmey.instrument

import android.graphics.Bitmap
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
            InstrumentVision(context).identify(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
