package io.github.kochkaev.kotlin.telegrambots.dsl

import io.github.kochkaev.kotlin.telegrambots.bots.KTelegramLongPollingBot
import io.github.kochkaev.kotlin.telegrambots.bots.KTelegramWebhookBot
import io.github.kochkaev.kotlin.telegrambots.core.KTelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.telegram.telegrambots.bots.DefaultBotOptions

/**
 * Creates and configures a [KTelegramBot] using a DSL.
 *
 * @param token The bot token.
 * @param username The bot's username (optional, will be fetched automatically if not provided).
 * @param botPath The bot's path for webhooks.
 * @param options A lambda to configure the [DefaultBotOptions].
 * @param scope The [CoroutineScope] to launch handlers in.
 * @param block A lambda with a [HandlersDsl] receiver to configure update handlers.
 * @return A new instance of [KTelegramBot].
 */
inline fun telegramBot(
    token: String,
    username: String? = null,
    botPath: String? = username,
    crossinline options: DefaultBotOptions.() -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    crossinline block: HandlersDsl.() -> Unit = {}
): KTelegramBot {
    val botOptions = DefaultBotOptions().apply(options)
    val bot = KTelegramBot(
        botToken = token,
        botUsername = username,
        botPath = botPath,
        coroutineScope = scope,
        options = botOptions
    )
    HandlersDsl(bot, scope, bot.updates).apply(block)
    return bot
}

/**
 * Creates and configures a [KTelegramLongPollingBot] using a DSL.
 *
 * @param token The bot token.
 * @param username The bot's username.
 * @param options A lambda to configure the [DefaultBotOptions].
 * @param scope The [CoroutineScope] to launch handlers in.
 * @param block A lambda with a [HandlersDsl] receiver to configure update handlers.
 * @return A new instance of [KTelegramLongPollingBot].
 */
inline fun longPollingBot(
    token: String,
    username: String? = null,
    crossinline options: DefaultBotOptions.() -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    crossinline block: HandlersDsl.() -> Unit
): KTelegramLongPollingBot {
    val botOptions = DefaultBotOptions().apply(options)
    val bot = KTelegramLongPollingBot(token, username, botOptions)
    HandlersDsl(bot, scope, bot.updates).apply(block)
    return bot
}

/**
 * Creates and configures a [KTelegramWebhookBot] using a DSL.
 *
 * @param token The bot token.
 * @param username The bot's username.
 * @param botPath The bot's path for webhooks.
 * @param options A lambda to configure the [DefaultBotOptions].
 * @param scope The [CoroutineScope] to launch handlers in.
 * @param block A lambda with a [HandlersDsl] receiver to configure update handlers.
 * @return A new instance of [KTelegramWebhookBot].
 */
inline fun webhookBot(
    token: String,
    username: String? = null,
    botPath: String? = username,
    crossinline options: DefaultBotOptions.() -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    crossinline block: HandlersDsl.() -> Unit
): KTelegramWebhookBot {
    val botOptions = DefaultBotOptions().apply(options)
    val bot = KTelegramWebhookBot(token, username, botPath, botOptions)
    HandlersDsl(bot, scope, bot.updates).apply(block)
    return bot
}
