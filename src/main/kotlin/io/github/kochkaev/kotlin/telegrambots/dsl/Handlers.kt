package io.github.kochkaev.kotlin.telegrambots.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import org.telegram.telegrambots.meta.generics.TelegramBot

/**
 * A DSL for configuring update handlers on top of a [kotlinx.coroutines.flow.Flow].
 * This class is intended to be extended by generated functions.
 */
open class HandlersDsl(
    internal val bot: AbsSender,
    internal val scope: CoroutineScope,
    internal val updates: Flow<Update>
)

/**
 * Registers a handler for all updates.
 */
fun HandlersDsl.onUpdate(handler: suspend AbsSender.(Update) -> Unit) {
    scope.launch {
        updates.collect { with(bot) { handler(it) } }
    }
}

/**
 * Registers a handler for commands.
 * A command is a message that starts with "/".
 */
fun HandlersDsl.onCommand(command: String, handler: suspend AbsSender.(Update) -> Unit) {
    scope.launch {
        val botUsername = (bot as? TelegramBot)?.botUsername
        val cmdRegex = if (botUsername != null) {
            Regex("^/$command(@$botUsername)?(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)
        } else {
            Regex("^/$command(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)
        }

        updates.filter {
            it.hasMessage() && it.message.isCommand && it.message.text.matches(cmdRegex)
        }.collect { with(bot) { handler(it) } }
    }
}
