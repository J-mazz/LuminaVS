package com.lumina.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)

class ModelDownloaderTest {
    private val server = MockWebServer()
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun testEnsureModelAvailable_downloadsAndVerifiesSha256() = runBlocking {
        val body = "small model file".toByteArray()
        val expectedSha = sha256Hex(body)
        server.enqueue(MockResponse().setBody(String(body)).addHeader("Content-Length", body.size))

        val url = server.url("/model.bin").toString()
        val md = ModelDownloader(
            context,
            modelFilename = "test-model.bin",
            modelUrl = url,
            minModelSize = 1,
            modelSha256 = expectedSha
        )

        val ok = md.ensureModelAvailable()
        assertThat(ok).isTrue()
        assertThat(md.isModelAvailable).isTrue()
        val file = File(md.modelPath)
        assertThat(file.exists()).isTrue()
        assertThat(file.readBytes()).isEqualTo(body)
        file.delete()
    }

    @Test
    fun testEnsureModelAvailable_checksumMismatch() = runBlocking {
        val body = "small model file".toByteArray()
        server.enqueue(MockResponse().setBody(String(body)).addHeader("Content-Length", body.size))

        val url = server.url("/model.bin").toString()
        val md = ModelDownloader(
            context,
            modelFilename = "test-model2.bin",
            modelUrl = url,
            minModelSize = 1,
            modelSha256 = "deadbeefdeadbeef"
        )

        val ok = md.ensureModelAvailable()
        assertThat(ok).isFalse()
        assertThat(md.isModelAvailable).isFalse()
        val file = File(md.modelPath)
        assertThat(file.exists()).isFalse()
    }
}
