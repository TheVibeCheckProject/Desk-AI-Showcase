package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-01: releases move to their own public repo and the source repo goes private; updates must keep working. */
class UpdateSourceTest {
    private val sha = "a".repeat(40)

    /** A fake GitHub where only [live] exists: its API names the newest commit and its version.json answers. */
    private fun github(live: String, asked: MutableList<String>) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            asked += url
            val (code, body) = when {
                !url.contains("/$live/") -> 404 to "Not Found"
                url.startsWith("https://api.github.com/") -> 200 to sha
                url.contains("version.json") -> 200 to """{"versionCode": 105, "versionName": "2.39.0"}"""
                else -> 404 to "Not Found"
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("text/plain".toMediaType())).build()
        })
        .build()

    @Test
    fun theReleasesRepoIsUsedFirstAndAlone() {
        val asked = mutableListOf<String>()
        val latest = UpdateSource.fetch(github("example-org/DeskAI-releases", asked))
        assertNotNull(latest)
        assertEquals("https://raw.githubusercontent.com/example-org/DeskAI-releases/$sha/DeskAI.apk",
            latest!!.apkUrl)
        assertEquals("https://raw.githubusercontent.com/example-org/DeskAI-releases/main/DeskAI.apk",
            latest.file("DeskAI.apk"))
        assertTrue("the source repo isn't asked once releases answer", asked.none { it.contains("/DeskAI/") })
    }

    @Test
    fun withoutTheReleasesRepoTheSourceRepoStillWorks() {
        // Before the releases repo has a build, and before or after the source repo's transfer
        for (home in listOf("example-user/DeskAI", "example-org/DeskAI")) {
            val asked = mutableListOf<String>()
            val latest = UpdateSource.fetch(github(home, asked))
            assertEquals("https://raw.githubusercontent.com/$home/$sha/DeskAI.apk", latest!!.apkUrl)
            assertTrue("the releases repo is tried first", asked.first().contains("example-org/DeskAI-releases"))
        }
    }

    @Test
    fun theDownloadFallbacksStartWithTheReleasesRepo() {
        val urls = UpdateSource.apkFallbacks()
        assertTrue(urls.first().contains("example-org/DeskAI-releases/main/"))
        assertTrue(urls.any { it.contains("example-user/DeskAI/main/") })
    }
}
