package io.github.kochkaev.kotlin.telegrambots.core.jackson

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kochkaev.kotlin.telegrambots.core.BotDeserializer
import io.github.kochkaev.kotlin.telegrambots.core.BotSerializer
import io.github.kochkaev.kotlin.telegrambots.core.TypeConstructor
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod
import java.io.Serializable
import java.lang.reflect.Type

object JacksonBotSerializer : BotSerializer {
    private val mapper = ObjectMapper()

    override fun serialize(any: Any): String {
        return mapper.writeValueAsString(any)
    }
}

object JacksonBotDeserializer : BotDeserializer {
    private val mapper = ObjectMapper()

    override fun <T : Serializable> deserialize(json: String, method: PartialBotApiMethod<T>): T {
        return method.deserializeResponse(json)
    }
    override fun <T : Serializable> deserialize(json: String, clazz: Class<T>): T {
        return mapper.readValue(json, clazz)
    }
    override fun deserialize(json: String, type: Type): Any {
        return mapper.readValue(json, mapper.typeFactory.constructType(type))
    }
}
