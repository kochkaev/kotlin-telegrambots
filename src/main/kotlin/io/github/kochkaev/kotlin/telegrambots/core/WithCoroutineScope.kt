package io.github.kochkaev.kotlin.telegrambots.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.telegram.telegrambots.meta.api.objects.Update

interface WithCoroutineScope {
    val coroutineScope: CoroutineScope
}