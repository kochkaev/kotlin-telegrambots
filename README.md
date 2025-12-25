# Kotlin Coroutine Extensions for TelegramBots

[![Build Status](https://img.shields.io/github/actions/workflow/status/kochkaev/kotlin-telegrambots/build.yml?branch=main)](https://github.com/kochkaev/kotlin-telegrambots/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kochkaev/kotlin-telegrambots.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.github.kochkaev%20AND%20a:kotlin-telegrambots)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A lightweight library that provides a powerful, idiomatic Kotlin layer on top of the popular Java [TelegramBots](https://github.com/rubenlagus/TelegramBots) library. It transforms the original API into a clean, concise, and modern coroutine-based experience.

## Overview

The official `TelegramBots` library is an excellent tool, but its Java-centric design can feel cumbersome in Kotlin. This project reimagines the developer experience, focusing on simplicity, type-safety, and the power of coroutines.

It provides a universal `KTelegramBot` class that can be run in either **Long Polling** or **Webhook** mode, and can be switched "on the fly". All asynchronous operations are exposed as `suspend` functions, and a `Flow<Update>` is provided for reactive update processing.

## Features

- **Universal Bot Class**: A single `KTelegramBot` class to rule them all. No need to choose between `TelegramLongPollingBot` or `TelegramWebhookBot` at compile time.
- **Coroutines First**: All `executeAsync` methods from `AbsSender` are converted into clean `suspend` functions (e.g., `executeK(...)`).
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
    implementation("io.github.kochkaev:kotlin-telegrambots:1.0.6+6.9.7.1") // Use the latest version
}
```

## Quick Start

Creating and running a bot is incredibly simple.

```kotlin
fun main() = runBlocking {
    // 1. Create the bot using the DSL
    val bot = telegramBot(
        token = "YOUR_BOT_TOKEN",
        // Username is optional, it will be fetched automatically on start
        scope = this 
    ) {
        // 2. Define your handlers here
        onCommand("start") {
            // 'this' is an AbsSender, so you can call extension functions directly
            val message = sendMessage(it.message.chatId.toString(), "Hello!")
            println("Sent message with ID: ${message.messageId}")
        }

        onMessage {
            // You get the Message object directly and safely
            println("New message: ${it.text}")
        }
    }

    // 3. Start the bot in your preferred mode
    bot.startLongPolling()

    println("Bot started!")
}
```

## How It Works

The library uses a combination of smart architecture and build-time code generation:

1.  **`KTelegramBot`**: The core class that inherits from a generated `DefaultKAbsSender`. It manages the bot's lifecycle and provides the `Flow<Update>`.
2.  **Receivers**: Internal `KLongPollingReceiver` and `KWebhookReceiver` classes implement the `LongPollingBot` and `WebhookBot` interfaces. Their only job is to receive updates and forward them to the `KTelegramBot`'s `Flow`. This is a clean implementation of the **Composition over Inheritance** principle.
3.  **Code Generation**:
    - **`GenerateKTelegramBotTask`**: Scans `DefaultAbsSender` and generates a base class with `suspend` versions of all `...Async` methods.
    - **`GenerateHandlersDslTask`**: Scans the `Update` class and generates type-safe `on...` extension functions for the `HandlersDsl`.
    - **`GenerateExtensionsTask` & `GenerateObjectBuildersTask`**: Generate high-level API methods and object builders.

This ensures the library is always up-to-date with the latest `TelegramBots` API while providing a truly modern Kotlin experience.

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
