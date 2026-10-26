package ai.colmey.instrument

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val build: Int, val url: String)

object UpdateChecker {
    private const val RELEASES = "https://api.github.com/repos/godofecht/colmey-AI-experiment/releases?per_page=20"

    fun selectLatest(json: String, currentBuild: Int): AppUpdate? {
        val releases = JSONArray(json)
        var latest: AppUpdate? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            val build = Regex("^v0\\.1\\.(\\d+)$").matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (build <= currentBuild || (latest != null && build <= latest.build)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.optString("name") == "colmey-ai.apk") {
                    val url = asset.optString("browser_download_url")
                    if (url.startsWith("https://github.com/godofecht/colmey-AI-experiment/releases/download/")) {
                        latest = AppUpdate(build, url)
                    }
                }
            }
        }
        return latest
    }

    fun fetch(currentBuild: Int): AppUpdate? {
        val connection = URL(RELEASES).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ColmeyAI-Android")
        return try {
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { selectLatest(it.readText(), currentBuild) }
        } finally {
            connection.disconnect()
        }
    }
}
