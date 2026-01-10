package io.github.kochkaev.kotlin.telegrambots.core

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod
import java.io.Serializable

/**
 * An interface for serializing bot methods to a JSON string.
 */
interface BotSerializer {
    fun serialize(any: Any): String
}

/**
 * An interface for deserializing a JSON response into a specific type,
 * using the original method object to determine the target type.
 */
interface BotDeserializer {
    fun <T : Serializable> deserialize(json: String, method: PartialBotApiMethod<T>): T
}
