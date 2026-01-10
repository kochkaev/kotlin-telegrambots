@file:Suppress("unused", "RedundantVisibilityModifier", "DEPRECATION")

package io.github.kochkaev.kotlin.telegrambots.dsl

import org.telegram.telegrambots.meta.TelegramUrl

/**
 * Builder function for [TelegramUrl].
 *
 * @see TelegramUrl
 */
public fun telegramUrl(
    schema: String = TelegramUrl.DEFAULT_URL.schema,
    host: String = TelegramUrl.DEFAULT_URL.host,
    port: Int = TelegramUrl.DEFAULT_URL.port,
    testServer: Boolean = TelegramUrl.DEFAULT_URL.isTestServer,
): TelegramUrl = TelegramUrl().apply {
    this.schema = schema
    this.host = host
    this.port = port
    this.isTestServer = testServer
}