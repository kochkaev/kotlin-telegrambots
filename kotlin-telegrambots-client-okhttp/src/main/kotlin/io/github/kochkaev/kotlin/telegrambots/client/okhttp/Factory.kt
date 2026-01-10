package io.github.kochkaev.kotlin.telegrambots.client.okhttp

import io.github.kochkaev.kotlin.telegrambots.core.KTelegramBotBuilder
import io.github.kochkaev.kotlin.telegrambots.core.KTelegramClient
import io.github.kochkaev.kotlin.telegrambots.dsl.HandlersScope
import io.github.kochkaev.kotlin.telegrambots.dsl.handlersScope
import org.telegram.telegrambots.meta.TelegramUrl

/**
 * Creates a new [KTelegramClient] with an OkHttp backend and Jackson for serialization.
 *
 * @param token The bot token.
 * @param telegramUrl Configured Telegram Bot API URL.
 * @return A new instance of [KTelegramClient].
 */
fun okHttpTelegramBot(
    token: String,
    telegramUrl: TelegramUrl = TelegramUrl.DEFAULT_URL,
    block: HandlersScope.() -> Unit = {}
): KTelegramClient {
    return KTelegramBotBuilder().token(token).telegramUrl(telegramUrl).apply {
        httpExecutor(OkHttpExecutor(botSerializer))
    }.build().apply { handlersScope.block() }
}
