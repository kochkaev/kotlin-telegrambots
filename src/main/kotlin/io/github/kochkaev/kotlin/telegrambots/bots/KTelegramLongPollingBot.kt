@file:Suppress("unused")

package io.github.kochkaev.kotlin.telegrambots.bots

import io.github.kochkaev.kotlin.telegrambots.core.FlowTelegramBot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * A [TelegramLongPollingBot] implementation that exposes a [Flow] of updates.
 */
class KTelegramLongPollingBot(
    token: String,
    private var botUsername: String? = null,
    options: DefaultBotOptions
) : TelegramLongPollingBot(options, token), FlowTelegramBot {
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

    override fun onUpdateReceived(update: Update) {
        _updates.tryEmit(update)
    }
}
