@file:Suppress("unused")

package io.github.kochkaev.kotlin.telegrambots.bots

import io.github.kochkaev.kotlin.telegrambots.core.FlowTelegramBot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * A [TelegramLongPollingBot] implementation that exposes a [Flow] of updates.
 */
open class KTelegramLongPollingBot(
    token: String,
    private val botUsername: String?,
    options: DefaultBotOptions
) : TelegramLongPollingBot(options, token), FlowTelegramBot {
    private val _updates = MutableSharedFlow<Update>()
    override val updates = _updates.asSharedFlow()

    override fun getBotUsername(): String? = botUsername

    override fun onUpdateReceived(update: Update) {
        _updates.tryEmit(update)
    }
}
