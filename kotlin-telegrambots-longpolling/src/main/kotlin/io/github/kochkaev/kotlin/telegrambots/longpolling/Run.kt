package io.github.kochkaev.kotlin.telegrambots.longpolling

import io.github.kochkaev.kotlin.telegrambots.core.KTelegramClient
import io.github.kochkaev.kotlin.telegrambots.dsl.deleteWebhook
import io.github.kochkaev.kotlin.telegrambots.dsl.emitUpdate
import io.github.kochkaev.kotlin.telegrambots.dsl.getUpdates
import io.github.kochkaev.kotlin.telegrambots.dsl.startSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

fun KTelegramClient.runLongPolling(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    timeout: Int = 60,
    pollingDelay: Long = 1000,
    errorDelay: Long = 5000,
) {
    startSession(coroutineScope.launch {
        deleteWebhook(false)
        var lastUpdateId = 0
        while (isActive) {
            val updates = try {
                getUpdates(offset = lastUpdateId + 1, timeout = timeout)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                e.printStackTrace()
                delay(errorDelay)
                continue
            }
            for (update in updates) { 
                emitUpdate(update)
                lastUpdateId = update.updateId
            }
            delay(pollingDelay)
        }
    })
}
