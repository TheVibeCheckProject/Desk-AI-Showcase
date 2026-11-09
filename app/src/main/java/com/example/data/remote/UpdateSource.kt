package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Where DeskAI finds its newest version. GitHub's raw file servers keep a copy for up to 5 minutes and ignore
 * `?t=` cache-busting, so right after a release the badge and the update screen could read different copies (the
 * owner saw "update available" and then "up to date", 2026-10-06), and a download could fetch the previous APK.
 * So: ask GitHub's API for the newest commit on main, then read version.json and the APK *at that commit*: a
 * path no cache can have an old copy of. If the API can't be reached (it allows 60 calls an hour), fall back to
 * the plain raw file.
 */
object UpdateSource {
    /**
     * Where releases live, first answer wins. 2026-11-01: releases move to a small public repo of their own
     * (example-org/DeskAI-releases: the APK and web_dist/version.json only), so the source repo can go private;
     * a private repo answers 404 to the app. The source repo's two addresses (before and after its transfer from
     * example-user to example-org) stay as fallbacks until it's private, then they just 404 and can be dropped.
     */
    val REPOS = listOf("example-org/DeskAI-releases", "example-org/DeskAI", "example-user/DeskAI")

    fun raw(repo: String) = "https://raw.githubusercontent.com/$repo"

    /** The plain (unpinned) APK addresses to try when nothing better is known, newest home first. */
    fun apkFallbacks(): List<String> = REPOS.flatMap { listOf("${raw(it)}/main/DeskAI.apk", "${raw(it)}/master/DeskAI.apk") }

    data class Latest(val json: JSONObject, val sha: String?, val repo: String = REPOS.last()) {
        val versionCode: Int get() = json.optInt("versionCode", 1)
        /** The APK of exactly this version (pinned to the commit), or null to use version.json's own link. */
        val apkUrl: String? get() = sha?.let { "${raw(repo)}/$it/DeskAI.apk" }
        /** A file from the repo that answered (version.json's relative downloadUrl). */
        fun file(path: String) = "${raw(repo)}/main/$path"
    }

    private fun newestCommit(client: OkHttpClient, repo: String): String? = try {
        val req = Request.Builder().url("https://api.github.com/repos/$repo/commits/main")
            .header("Accept", "application/vnd.github.sha")
            .header("User-Agent", "DeskAI-Android")
            .build()
        client.newCall(req).execute().use { resp ->
            val sha = resp.body?.string()?.trim().orEmpty()
            if (resp.isSuccessful && Regex("^[0-9a-f]{40}$").matches(sha)) sha else null
        }
    } catch (_: Exception) {
        null
    }

    /** The newest version.json (blocking: call off the main thread), or null if GitHub can't be reached. */
    fun fetch(client: OkHttpClient): Latest? {
        for (repo in REPOS) {                       // the first home that answers wins (see REPOS)
            val sha = newestCommit(client, repo)
            val candidates = listOfNotNull(
                sha?.let { "${raw(repo)}/$it/web_dist/version.json" to it },
                "${raw(repo)}/main/web_dist/version.json?t=${System.currentTimeMillis()}" to null
            )
            for ((url, pinned) in candidates) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", "DeskAI-Android").build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string()?.trim().orEmpty()
                        if (resp.isSuccessful && body.startsWith("{") && body.contains("versionCode")) {
                            return Latest(JSONObject(body), pinned, repo)
                        }
                    }
                } catch (_: Exception) {
                    // try the next one
                }
            }
        }
        return null
    }
}
