package io.github.kochkaev.kotlin.telegrambots.core.receivers

import io.github.kochkaev.kotlin.telegrambots.core.KTelegramBot
import io.github.kochkaev.kotlin.telegrambots.dsl.deleteWebhook
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.WebhookBot
import org.telegram.telegrambots.util.WebhookUtils

/**
 * An internal receiver that implements the [WebhookBot] logic
 * and delegates all actions to a central [KTelegramBot] instance.
 * Its only job is to receive updates and forward them.
 */
internal class KWebhookReceiver(private val master: KTelegramBot) : WebhookBot, BotReceiver {

    override fun getBotToken(): String = master.botToken
    override fun getBotUsername(): String? = master.botUsername
    override fun getBotPath(): String? = master.botPath

    override fun onWebhookUpdateReceived(update: Update): Nothing? {
        master.emitUpdate(update)
        return null
    }

    override fun setWebhook(setWebhook: SetWebhook) {
        WebhookUtils.setWebhook(master, this, setWebhook)
    }

    override suspend fun stop() {
        master.deleteWebhook(false)
    }
}
