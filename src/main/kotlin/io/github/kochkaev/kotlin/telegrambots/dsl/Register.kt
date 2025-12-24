package io.github.kochkaev.kotlin.telegrambots.dsl

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.generics.BotSession
import org.telegram.telegrambots.meta.generics.Webhook
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook

/**
 * Registers the bot with a new [TelegramBotsApi] instance.
 *
 * @return The created [BotSession] instance, which can be used to stop the bot.
 * @throws org.telegram.telegrambots.meta.exceptions.TelegramApiException on registration error.
 */
fun TelegramLongPollingBot.register(botSessionClass: Class<out BotSession> = DefaultBotSession::class.java): BotSession {
    val botsApi = TelegramBotsApi(botSessionClass)
    return botsApi.registerBot(this)
}

/**
 * Registers the bot with a new [TelegramBotsApi] instance using [DefaultBotSession].
 *
 * @throws org.telegram.telegrambots.meta.exceptions.TelegramApiException on registration error.
 */
fun TelegramWebhookBot.register(setWebhook: SetWebhook, webhook: Webhook) {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java, webhook)
    botsApi.registerBot(this, setWebhook)
}
inline fun TelegramWebhookBot.register(webhook: Webhook, crossinline block: SetWebhook.() -> Unit) {
    register(SetWebhook().apply(block), webhook)
}
inline fun TelegramWebhookBot.register(setWebhook: SetWebhook, crossinline block: DefaultWebhook.() -> Unit) {
    register(setWebhook, DefaultWebhook().apply(block))
}
inline fun TelegramWebhookBot.register(crossinline setWebhookBlock: SetWebhook.() -> Unit, crossinline webhookBlock: DefaultWebhook.() -> Unit) {
    register(SetWebhook().apply(setWebhookBlock), DefaultWebhook().apply(webhookBlock))
}
