package io.github.kochkaev.kotlin.telegrambots.core

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod
import java.io.Serializable
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

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
    fun <T : Serializable> deserialize(json: String, clazz: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return deserialize(json, clazz as Type) as T
    }
    fun deserialize(json: String, type: Type): Any
}

/**
 * A helper class to capture generic type information for deserialization
 * without depend on TypeReference of Jackson or something like it.
 * This is used to provide `Type` instances to deserializers when the target type
 * is generic and cannot be directly represented by a `Class` object.
 */
abstract class TypeConstructor <T> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
}
/**
 * Returns a [Type] instance for the reified type parameter [T].
 * This is useful for deserializing generic types where a [Class] object is insufficient.
 * This function may be used with any deserializer (Jackson, Gson, etc.).
 */
inline fun <reified T> reifiedToType(): Type
    = object: TypeConstructor<T>(){}.type