package io.github.kochkaev.kotlin.telegrambots.core

import org.telegram.telegrambots.meta.TelegramUrl
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/**
 * Base interface for our bot, defining the token.
 */
interface KTelegramBot {
    val token: String
    val telegramUrl: TelegramUrl
}

/**
 * Defines the core components required for a modular Telegram client.
 * This includes an HTTP executor, a serializer, and a deserializer.
 */
interface ModularTelegramClient {
    val executor: HttpExecutor
    val serializer: BotSerializer
    val deserializer: BotDeserializer
}

/**
 * The main, all-encompassing client interface for our library.
 * It combines the official TelegramClient with our own base bot and file downloading capabilities.
 */
interface KTelegramClient : KTelegramBot, ModularTelegramClient, TelegramClientSuspendable


sealed interface Part {
    val key: String
}
data class StringPart(override val key: String, val value: String) : Part
data class JsonPart(override val key: String, val value: Any) : Part
data class FilePart(override val key: String, val value: Any) : Part


/**
 * A "dumb executor" that only knows how to send requests over the network.
 * It doesn't know about Telegram methods, only about HTTP methods and request bodies.
 */
interface HttpExecutor {
    /**
     * Executes a simple JSON-based request.
     * @param url The full URL for the request.
     * @param json The JSON body of the request.
     * @return A CompletableFuture with the raw JSON response.
     */
    fun executeJson(url: String, json: String): CompletableFuture<String>

    /**
     * Executes a complex multipart/form-data request.
     * @param url The full URL for the request.
     * @param parts A list of typed parts to include in the request.
     * @return A CompletableFuture with the raw JSON response.
     */
    fun executeMultipart(url: String, parts: List<Part>): CompletableFuture<String>

    /**
     * Downloads a file from a given URL.
     * @param url The full URL to the file.
     * @return A CompletableFuture with the InputStream of the file content.
     */
    fun downloadFile(url: String): CompletableFuture<InputStream>

    /**
     * Starts a server to receive webhook updates.
     * @param port The port to listen on.
     * @param secretToken The secret token to check for in the X-Telegram-Bot-Api-Secret-Token header.
     * @param onUpdate A callback to be invoked for each update.
     * @return A Stoppable object that can be used to stop the server.
     */
    fun startServer(port: Int, secretToken: String?, onUpdate: (String) -> Unit): Stoppable
}

/**
 * An interface for objects that can be stopped.
 */
interface Stoppable {
    /**
     * Stops the object.
     */
    fun stop()
}
