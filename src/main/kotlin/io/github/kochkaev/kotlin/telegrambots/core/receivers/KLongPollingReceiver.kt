package io.github.kochkaev.kotlin.telegrambots.core.receivers

import io.github.kochkaev.kotlin.telegrambots.core.KTelegramBot
import io.github.kochkaev.kotlin.telegrambots.deleteWebhook
import kotlinx.coroutines.runBlocking
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.BotOptions
import org.telegram.telegrambots.meta.generics.BotSession
import org.telegram.telegrambots.meta.generics.LongPollingBot

/**
 * An internal receiver that implements the [LongPollingBot] logic
 * and delegates all actions to a central [KTelegramBot] instance.
 * Its only job is to receive updates and forward them.
 */
internal class KLongPollingReceiver(private val master: KTelegramBot) : LongPollingBot, BotReceiver {

    override fun getBotToken(): String? = master.botToken
    override fun getBotUsername(): String? = master.botUsername
    override fun getOptions(): BotOptions = master.options

    override fun onUpdateReceived(update: Update) {
        master.mutableUpdates.tryEmit(update)
    }

    override fun clearWebhook() {
        runBlocking {
            master.deleteWebhook(false)
        }
    }

    internal var session: BotSession? = null
    override suspend fun stop() {
        session?.stop()
    }
}
