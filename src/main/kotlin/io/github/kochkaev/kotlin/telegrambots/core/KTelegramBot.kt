@file:Suppress("unused")

package io.github.kochkaev.kotlin.telegrambots.core

import io.github.kochkaev.kotlin.telegrambots.core.receivers.BotReceiver
import io.github.kochkaev.kotlin.telegrambots.core.receivers.KLongPollingReceiver
import io.github.kochkaev.kotlin.telegrambots.core.receivers.KWebhookReceiver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramBot
import org.telegram.telegrambots.meta.generics.Webhook
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook

/**
 * A universal bot class that can be started in either Long Polling or Webhook mode.
 * It provides suspendable versions of `execute` methods and a Flow of updates.
 *
 * @param botToken The Telegram Bot API token.
 * @param botUsername The bot's username. If null, it will be fetched automatically on start.
 * @param botPath The bot's path for webhooks.
 * @param options Custom bot options.
 */
class KTelegramBot(
    private val botToken: String,
    botUsername: String? = null,
    var botPath: String? = botUsername,
    options: DefaultBotOptions = DefaultBotOptions(),
) : DefaultKAbsSender(options, botToken), FlowTelegramBot, TelegramBot {

    // Store the username provided in the constructor, or the one we fetch.
    private var resolvedBotUsername: String? = botUsername
    override fun getBotUsername(): String? = resolvedBotUsername

    internal val mutableUpdates = MutableSharedFlow<Update>()
    override val updates: Flow<Update> = mutableUpdates.asSharedFlow()

    private var receiver: BotReceiver? = null

    val isRunning: Boolean
        get() = receiver != null
    val isLongPollingRunning: Boolean
        get() = receiver is KLongPollingReceiver
    val isWebhookRunning: Boolean
        get() = receiver is KWebhookReceiver

    /**
     * Fetches the bot's username using getMe() if it hasn't been provided or fetched already.
     */
    private suspend fun getOrFetchUsername(): String {
        resolvedBotUsername?.let { return it }
        val me = sendApiMethodK(GetMe())
        return me.userName.also { resolvedBotUsername = it }
    }

    fun modifyOptions(block: DefaultBotOptions.() -> Unit) {
        options.block()
    }

    /**
     * Starts the bot in Long Polling mode. If a session is already active, it will be stopped and replaced.
     */
    suspend fun startLongPolling() {
        receiver?.stop()
        getOrFetchUsername() // Ensure username is resolved before starting
        val longPollingReceiver = KLongPollingReceiver(this)
        receiver = longPollingReceiver
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        longPollingReceiver.session = botsApi.registerBot(longPollingReceiver)
    }

    /**
     * Starts the bot in Webhook mode. If a session is already active, it will be stopped and replaced.
     */
    suspend fun startWebhook(setWebhook: SetWebhook, webhook: Webhook = DefaultWebhook()) {
        receiver?.stop()
        getOrFetchUsername() // Ensure username is resolved before starting
        val webhookReceiver = KWebhookReceiver(this)
        receiver = webhookReceiver
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java, webhook)
        botsApi.registerBot(webhookReceiver, setWebhook)
    }

    suspend inline fun startWebhook(webhook: Webhook, crossinline block: SetWebhook.() -> Unit) {
        startWebhook(SetWebhook().apply(block), webhook)
    }

    suspend inline fun startWebhook(setWebhook: SetWebhook, crossinline block: DefaultWebhook.() -> Unit) {
        startWebhook(setWebhook, DefaultWebhook().apply(block))
    }

    suspend inline fun startWebhook(crossinline setWebhookBlock: SetWebhook.() -> Unit, crossinline webhookBlock: DefaultWebhook.() -> Unit) {
        startWebhook(SetWebhook().apply(setWebhookBlock), DefaultWebhook().apply(webhookBlock))
    }

    /**
     * Stops the currently active bot session.
     */
    suspend fun stop() {
        receiver?.stop()
        receiver = null
    }
}
