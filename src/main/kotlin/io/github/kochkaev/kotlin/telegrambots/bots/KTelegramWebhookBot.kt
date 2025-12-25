@file:Suppress("unused")

package io.github.kochkaev.kotlin.telegrambots.bots

import io.github.kochkaev.kotlin.telegrambots.core.FlowTelegramBot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * A [TelegramWebhookBot] implementation that exposes a [kotlinx.coroutines.flow.Flow] of updates.
 */
open class KTelegramWebhookBot(
    token: String,
    private val botUsername: String?,
    private val botPath: String?,
    options: DefaultBotOptions
) : TelegramWebhookBot(options, token), FlowTelegramBot {
    private val _updates = MutableSharedFlow<Update>()
    override val updates = _updates.asSharedFlow()

    override fun getBotUsername(): String? = botUsername
    override fun getBotPath(): String? = botPath

    override fun onWebhookUpdateReceived(update: Update): Nothing? {
        _updates.tryEmit(update)
        return null
    }
}
