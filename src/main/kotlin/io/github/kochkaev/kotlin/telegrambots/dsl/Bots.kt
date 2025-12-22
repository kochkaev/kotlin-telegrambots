package io.github.kochkaev.kotlin.telegrambots.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * A base class for bots that provides a [Flow] of updates.
 */
abstract class FlowTelegramBot(token: String) : TelegramLongPollingBot(token) {
    private val _updates = MutableSharedFlow<Update>()
    val updates = _updates.asSharedFlow()

    override fun onUpdateReceived(update: Update) {
        _updates.tryEmit(update)
    }
}

/**
 * Creates a new [TelegramLongPollingBot] with a [Flow]-based update handling and a DSL.
 *
 * @param token The bot token.
 * @param scope The [CoroutineScope] to launch handlers in.
 * @param block A lambda with a [HandlersDsl] receiver to configure update handlers.
 * @return A new instance of [TelegramLongPollingBot].
 */
inline fun longPollingBot(
    token: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    crossinline block: HandlersDsl.() -> Unit = { -> }
): TelegramLongPollingBot {
    val bot = object : FlowTelegramBot(token) {
        override fun getBotUsername(): String? = null
    }
    HandlersDsl(bot, scope, bot.updates).apply(block)
    return bot
}
