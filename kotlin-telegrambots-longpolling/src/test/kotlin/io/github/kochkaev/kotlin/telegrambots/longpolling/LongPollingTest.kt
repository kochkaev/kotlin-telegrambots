package io.github.kochkaev.kotlin.telegrambots.longpolling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.kochkaev.kotlin.telegrambots.dsl.session
import io.github.kochkaev.kotlin.telegrambots.dsl.updates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.ApiResponse
import org.telegram.telegrambots.meta.api.objects.Update
import kotlin.test.assertEquals

class LongPollingTest : AbsTest() {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun updatesAreReceived(): Unit = runBlocking {
        val deleteWebhookResponse = ApiResponse
            .builder<Boolean>()
            .ok(true)
            .result(true)
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(deleteWebhookResponse)))

        val updates = listOf(
            Update().apply { updateId = 1 },
            Update().apply { updateId = 2 }
        )
        val apiResponse = ApiResponse
            .builder<List<Update>>()
            .ok(true)
            .result(updates)
            .build()
        val emptyApiResponse = ApiResponse
            .builder<List<Update>>()
            .ok(true)
            .result(emptyList())
            .build()
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(apiResponse)))
        webServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(emptyApiResponse))) // To stop long polling

        launch {
            client.runLongPolling(coroutineScope = this)
        }
        delay(100) // Wait for session to be set

        val receivedUpdate = withTimeout(5000) {
            client.updates.first()
        }
        assertEquals(1, receivedUpdate.updateId)
        client.session?.cancel()
    }
}
