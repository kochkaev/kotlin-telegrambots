package io.github.kochkaev.kotlin.telegrambots.webhook

import io.github.kochkaev.kotlin.telegrambots.core.KTelegramClient
import io.github.kochkaev.kotlin.telegrambots.dsl.deleteWebhook
import io.github.kochkaev.kotlin.telegrambots.dsl.emitUpdate
import io.github.kochkaev.kotlin.telegrambots.dsl.setWebhook
import io.github.kochkaev.kotlin.telegrambots.dsl.startSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update

fun KTelegramClient.runWebhook(
    url: String,
    port: Int,
    certificate: InputFile? = null,
    ipAddress: String? = null,
    maxConnections: Int? = null,
    secretToken: String? = null,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val server = executor.startServer(port, secretToken) { json ->
        coroutineScope.launch {
            val update = deserializer.deserialize(json, Update::class.java)
            emitUpdate(update)
        }
    }

    val sessionJob = Job()
    startSession(sessionJob)

    sessionJob.invokeOnCompletion {
        server.stop()
        coroutineScope.launch {
            deleteWebhook(false)
        }
    }

    coroutineScope.launch {
        setWebhook(
            url = url,
            certificate = certificate,
            ipAddress = ipAddress,
            maxConnections = maxConnections,
            secretToken = secretToken,
        )
    }
}
