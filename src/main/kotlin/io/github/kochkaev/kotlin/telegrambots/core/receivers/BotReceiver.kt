package io.github.kochkaev.kotlin.telegrambots.core.receivers

interface BotReceiver {
    suspend fun stop()
}