package ai.colmey.instrument

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTest {
    @Test fun discoversNewestVersionedApk() {
        val releases = """[
          {"tag_name":"v0.1.8","draft":false,"assets":[{"name":"colmey-ai.apk","browser_download_url":"https://github.com/godofecht/colmey-AI-experiment/releases/download/v0.1.8/colmey-ai.apk"}]},
          {"tag_name":"v0.1.9","draft":false,"assets":[{"name":"colmey-ai.apk","browser_download_url":"https://github.com/godofecht/colmey-AI-experiment/releases/download/v0.1.9/colmey-ai.apk"}]}
        ]"""
        assertEquals(9, UpdateChecker.selectLatest(releases, 7)?.build)
        assertNull(UpdateChecker.selectLatest(releases, 9))
    }

    @Test fun ignoresDraftsAndUntrustedUrls() {
        val releases = """[
          {"tag_name":"v0.1.999","draft":true,"assets":[{"name":"colmey-ai.apk","browser_download_url":"https://github.com/godofecht/colmey-AI-experiment/releases/download/v0.1.999/colmey-ai.apk"}]},
          {"tag_name":"v0.1.50","draft":false,"assets":[{"name":"colmey-ai.apk","browser_download_url":"https://evil.example/app.apk"}]}
        ]"""
        assertNull(UpdateChecker.selectLatest(releases, 1))
    }
}
