package io.github.kochkaev.kotlin.telegrambots.core

import io.github.kochkaev.kotlin.telegrambots.core.jackson.JacksonBotDeserializer
import io.github.kochkaev.kotlin.telegrambots.core.jackson.JacksonBotSerializer
import io.github.kochkaev.kotlin.telegrambots.dsl.HandlersScope
import org.telegram.telegrambots.meta.TelegramUrl

class KTelegramBotBuilder {
    var token: String? = null
    var telegramUrl: TelegramUrl = TelegramUrl.DEFAULT_URL
    var httpExecutor: HttpExecutor? = null
    var botSerializer: BotSerializer = JacksonBotSerializer
    var botDeserializer: BotDeserializer = JacksonBotDeserializer

    fun token(token: String) = apply { this.token = token }
    fun telegramUrl(telegramUrl: TelegramUrl) = apply { this.telegramUrl = telegramUrl }
    fun httpExecutor(httpExecutor: HttpExecutor) = apply { this.httpExecutor = httpExecutor }
    fun botSerializer(botSerializer: BotSerializer) = apply { this.botSerializer = botSerializer }
    fun botDeserializer(botDeserializer: BotDeserializer) = apply { this.botDeserializer = botDeserializer }

    fun build(): KTelegramClient {
        val token = requireNotNull(token) { "Token must be set" }
        val httpExecutor = requireNotNull(httpExecutor) { "HttpExecutor must be set" }

        return DefaultKTelegramClient(token, telegramUrl, httpExecutor, botSerializer, botDeserializer)
    }

}