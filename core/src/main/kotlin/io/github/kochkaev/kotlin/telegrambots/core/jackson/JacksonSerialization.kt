package io.github.kochkaev.kotlin.telegrambots.core.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kochkaev.kotlin.telegrambots.core.BotDeserializer
import io.github.kochkaev.kotlin.telegrambots.core.BotSerializer
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod
import java.io.Serializable

object JacksonBotSerializer : BotSerializer {
    private val mapper = ObjectMapper()

    override fun serialize(any: Any): String {
        return mapper.writeValueAsString(any)
    }
}

object JacksonBotDeserializer : BotDeserializer {
    override fun <T : Serializable> deserialize(json: String, method: PartialBotApiMethod<T>): T {
        return method.deserializeResponse(json)
    }
}
