package io.github.kochkaev.kotlin.telegrambots.core

import io.github.kochkaev.kotlin.telegrambots.dsl.longPollingBot
import io.github.kochkaev.kotlin.telegrambots.dsl.onMessage
import io.github.kochkaev.kotlin.telegrambots.sendMessage
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

/**
 * A bot implementation that extends [DefaultAbsSender] to provide suspendable (coroutine-based) versions of execute methods.
 * This class leverages the existing logic from `DefaultAbsSender` for handling file uploads and other complex requests,
 * and simply wraps the asynchronous `CompletableFuture` methods into `suspend` functions.
 *
 * @param options The bot options.
 * @param token The Telegram Bot API token.
 */
open class KTelegramBot(
    options: DefaultBotOptions,
    val token: String,
) : DefaultAbsSender(options, token) {
    init { TODO("Not implemented yet") }
}

/**
 * Not implemented yet
 * Inline factory function to easily create a [KTelegramBot].
 *
 * @param token The Telegram Bot API token.
 * @param options A lambda to configure the [DefaultBotOptions].
 * @return A new instance of [KTelegramBot].
 */
@Deprecated("Not implemented yet")
inline fun telegramBot(
    token: String,
    options: DefaultBotOptions.() -> Unit = {}
): KTelegramBot {
    val botOptions = DefaultBotOptions().apply(options)
    return KTelegramBot(botOptions, token)
}
