package io.github.kochkaev.kotlin.telegrambots.core

import kotlinx.coroutines.flow.Flow
import org.telegram.telegrambots.meta.api.objects.Update

/**
 * An interface for bots that expose a [Flow] of updates.
 */
interface FlowTelegramBot {
    val updates: Flow<Update>
}