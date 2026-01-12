package io.github.kochkaev.kotlin.telegrambots.longpolling

import io.github.kochkaev.kotlin.telegrambots.client.okhttp.OkHttpExecutor
import io.github.kochkaev.kotlin.telegrambots.core.KTelegramBotBuilder
import io.github.kochkaev.kotlin.telegrambots.core.KTelegramClient
import io.github.kochkaev.kotlin.telegrambots.core.jackson.JacksonBotDeserializer
import io.github.kochkaev.kotlin.telegrambots.core.jackson.JacksonBotSerializer
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.telegram.telegrambots.meta.TelegramUrl

abstract class AbsTest {
    lateinit var webServer: MockWebServer
    lateinit var client: KTelegramClient

    @BeforeEach
    fun setUp() {
        webServer = MockWebServer()
        webServer.start()

        val telegramUrl = TelegramUrl.builder()
            .schema(webServer.url("").scheme)
            .host(webServer.url("").host)
            .port(webServer.url("").port)
            .build()

        val serializer = JacksonBotSerializer
        val deserializer = JacksonBotDeserializer
        val executor = OkHttpExecutor(serializer)
        client = KTelegramBotBuilder()
            .token("TEST_TOKEN")
            .telegramUrl(telegramUrl)
            .botSerializer(serializer)
            .botDeserializer(deserializer)
            .httpExecutor(executor)
            .build()
    }

    @AfterEach
    fun tearDown() {
        webServer.shutdown()
    }
}
