package io.github.kochkaev.kotlin.telegrambots.webhook

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.kochkaev.kotlin.telegrambots.dsl.session
import io.github.kochkaev.kotlin.telegrambots.dsl.updates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.ApiResponse
import org.telegram.telegrambots.meta.api.objects.Update
import kotlin.test.assertEquals

class WebhookTest : AbsTest() {

    private val objectMapper = jacksonObjectMapper()
    private val okHttpClient = OkHttpClient()

    @Test
    fun updatesAreReceived(): Unit = runBlocking {
        val setWebhookResponse = ApiResponse
            .builder<Boolean>()
            .ok(true)
            .result(true)
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(setWebhookResponse)))

        val deleteWebhookResponse = ApiResponse
            .builder<Boolean>()
            .ok(true)
            .result(true)
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(deleteWebhookResponse)))

        val port = webServer.port + 1
        val url = "http://localhost:$port"

        launch {
            client.runWebhook(url = url, port = port)
        }
        delay(100) // Wait for session to be set

        val update = Update().apply { updateId = 1 }
        val request = Request.Builder()
            .url(url)
            .post(objectMapper.writeValueAsString(update).toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute()

        val receivedUpdate = withTimeout(5000) {
            client.updates.first()
        }
        assertEquals(1, receivedUpdate.updateId)
        client.session?.cancel()
    }

    @Test
    fun secretTokenIsChecked(): Unit = runBlocking {
        val setWebhookResponse = ApiResponse
            .builder<Boolean>()
            .ok(true)
            .result(true)
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(setWebhookResponse)))

        val deleteWebhookResponse = ApiResponse
            .builder<Boolean>()
            .ok(true)
            .result(true)
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(deleteWebhookResponse)))

        val port = webServer.port + 1
        val url = "http://localhost:$port"
        val secretToken = "SECRET_TOKEN"

        launch {
            client.runWebhook(url = url, port = port, secretToken = secretToken)
        }
        delay(100) // Wait for session to be set

        val update = Update().apply { updateId = 1 }
        val request = Request.Builder()
            .url(url)
            .header("X-Telegram-Bot-Api-Secret-Token", secretToken)
            .post(objectMapper.writeValueAsString(update).toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute()

        val receivedUpdate = withTimeout(5000) {
            client.updates.first()
        }
        assertEquals(1, receivedUpdate.updateId)

        val requestWithoutToken = Request.Builder()
            .url(url)
            .post(objectMapper.writeValueAsString(update).toRequestBody("application/json".toMediaType()))
            .build()
        val response = okHttpClient.newCall(requestWithoutToken).execute()
        assertEquals(403, response.code)

        client.session?.cancel()
    }
}
