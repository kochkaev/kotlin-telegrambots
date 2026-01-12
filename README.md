# Kotlin Coroutine Extensions for TelegramBots

[![Build Status](https://img.shields.io/github/actions/workflow/status/release.yml?branch=main)](https://github.com/kochkaev/kotlin-telegrambots/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kochkaev/kotlin-telegrambots-meta.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.github.kochkaev%20AND%20a:kotlin-telegrambots-meta)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A lightweight library that provides a powerful, idiomatic Kotlin layer on top of the popular Java [TelegramBots](https://github.com/rubenlagus/TelegramBots) library. It transforms the original API into a clean, concise, and modern coroutine-based experience.

## Overview

The official `TelegramBots` library is an excellent tool, but its Java-centric design can feel cumbersome in Kotlin. This project reimagines the developer experience, focusing on simplicity, type-safety, and the power of coroutines.

It provides a universal `TelegramClient` that can be used with different backends (long polling and webhook) and can be switched "on the fly". All asynchronous operations are exposed as `suspend` functions, and a `Flow<Update>` is provided for reactive update processing.

## Features

- **Universal `KTelegramClient`**: A single `KTelegramClient` interface to rule them all.
- **Coroutines First**: All `executeAsync` methods from `TelegramClient` are converted into clean `suspend` functions.
- **Reactive Updates with Flow**: Get a `Flow<Update>` from your bot and use the full power of `kotlinx.coroutines.flow` to handle updates.
- **Type-Safe DSL for Handlers**: A clean, auto-generated DSL to handle different update types (`onMessage`, `onCallbackQuery`, etc.) in a type-safe manner.
- **High-Level API & Object Builders**: Still provides the convenient extension functions (`bot.sendMessage(...)`) and builders (`replyParameters(...)`) for a truly idiomatic experience.
- **Auto-Generated & Up-to-Date**: Code generation tasks scan the `TelegramBots` library to ensure the API is always current.

## Installation

Add the library to your project's dependencies.

#### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    // Use latest version
    implementation("io.github.kochkaev:kotlin-telegrambots-longpolling:1.1.2+9.2.1") // For long polling
    implementation("io.github.kochkaev:kotlin-telegrambots-client-okhttp:1.1.2+9.2.1") // For the OkHttp implementation of requesting
}
```

## Quick Start

Creating and running a bot is incredibly simple.

```kotlin
fun main() = runBlocking {
    // 1. Create the bot using the DSL
    val client = okHttpTelegramBot(
        token = "YOUR_BOT_TOKEN"
    ) {
        // 2. Define your handlers here
        onCommand("start") {
            // 'this' is a TelegramClient, so you can call extension functions directly
            val message = sendMessage(
                chatId = it.chat.id, 
                text = "Hello!"
            )
            println("Sent message with ID: ${message.messageId}")
        }

        onMessage {
            // You get the Message object directly and safely
            println("New message: ${it.text}")
        }
    }

    // 3. Start getting updates in your preferred mode
    client.runLongPolling()

    println("Bot started!")
    
    // 4. Stop getting updates
    client.session?.cancel()
}
```

## How It Works

The library uses a combination of smart architecture and build-time code generation:

1.  **`kotlin-telegrambots-meta`**: This module provides wrapper functions for objects and methods from `telegrambots-meta`.
2.  **`kotlin-telegrambots-core`**: This module contains the `DefaultKTelegramClient`, an auto-generated implementation of `TelegramClient` that is abstracted away from the underlying HTTP client.
3.  **`kotlin-telegrambots-client-okhttp`**: This module provides an implementation of the `HttpExecutor` interface from the `core` module using `OkHttp` as the backend.
4.  **`kotlin-telegrambots-longpolling`**: This module provides a long polling implementation for receiving updates.
5.  **`kotlin-telegrambots-handlers-dsl`**: This module provides a type-safe DSL for handling updates.
6.  **Code Generation**:
    - **`GenerateAbstractKTelegramClientTask`**: Scans `TelegramClient` and generates a base class with implemented non `...Async` methods to refer to them.
    - **`GenerateTelegramClientSuspendableTask`**: Scans `TelegramClient` and generates an interface with `suspend` versions of all `...Async` methods.
    - **`GenerateDefaultKTelegramClientTask`**: Generates the default implementation of the `KTelegramClient`, abstracted from underlying HTTP client and based on `OkHttpTelegramClient` from `telegrambots-client`.
    - **`GenerateHandlersDslTask`**: Scans the `Update` class and generates type-safe `on...` extension functions for the `HandlersDsl`.
    - **`GenerateTelegramBotExtensionsTask` & `GenerateObjectBuildersTask`**: Generate high-level API methods and object builders by using the `builder` pattern instead of constructors. This makes the library more robust and compatible with the latest versions of `TelegramBots`.

## Using `meta` and `handlers-dsl` without `core`

The `core` module (and its dependents like `client-okhttp` and `longpolling`) implements its own `TelegramClient` logic, which may not be fully compatible with the original library. If you want to use the `meta` and `handlers-dsl` modules with the original `TelegramBots` client, you can do so by following these steps:

1.  **Add the required dependencies:**
    ```kotlin
    // build.gradle.kts
    dependencies {
        // Use latest version
        implementation("org.telegram:telegrambots-client:9.2.1")
        implementation("org.telegram:telegrambots-longpolling:9.2.1")
        implementation("io.github.kochkaev:kotlin-telegrambots-meta:1.1.2+9.2.1")
        implementation("io.github.kochkaev:kotlin-telegrambots-handlers-dsl:1.1.2+9.2.1")
    }
    ```
2.  **Set up the update consumer:**
    ```kotlin
    val token = "YOUR_BOT_TOKEN"
    val client = OkHttpTelegramClient(token) // From telegrambots-client library
    val application = TelegramBotsLongPollingApplication() // From telegrambots-longpolling library
    application.registerBot(token) { update ->
        client.emitUpdate(update) 
    }
    with(client.handlersScope) {
        onMessage { message ->
            sendMessage(
                chatId = message.chat.id,
                text = "Hello, world!",
                replyParameters = replyParameters(
                    chatId = message.chat.id,
                    messageId = message.messageId
                )
            )
            // Your code
        }
        // Other handlers
    }
    ```

## Building From Source

To build the project locally, simply run the following command:

```sh
./gradlew build
```

To publish the artifacts to your local Maven repository (`~/.m2/repository`), run:

```sh
./gradlew publishToMavenLocal
```

## License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE.txt) file for details.
