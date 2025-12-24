@file:Suppress("unused")

package io.github.kochkaev.kotlin.telegrambots.bots

import io.github.kochkaev.kotlin.telegrambots.core.FlowTelegramBot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * A [TelegramWebhookBot] implementation that exposes a [kotlinx.coroutines.flow.Flow] of updates.
 */
class KTelegramWebhookBot(
    token: String,
    private var botUsername: String? = null,
    private val botPath: String? = botUsername,
    options: DefaultBotOptions
) : TelegramWebhookBot(options, token), FlowTelegramBot {
    private val _updates = MutableSharedFlow<Update>()
    override val updates = _updates.asSharedFlow()

    /**
     * Fetches the bot's username using getMe() if it hasn't been provided or fetched already.
     */
    private fun getOrFetchUsername(): String {
        botUsername?.let { return it }
        val me = sendApiMethod(GetMe())
        return me.userName.also { botUsername = it }
    }

    override fun getBotUsername(): String? = getOrFetchUsername()
    override fun getBotPath(): String? = botPath

    override fun onWebhookUpdateReceived(update: Update): Nothing? {
        _updates.tryEmit(update)
        return null
    }
}
