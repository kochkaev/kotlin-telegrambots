# Kotlin Coroutine Extensions for TelegramBots

[![Build Status](https://img.shields.io/github/actions/workflow/status/your-username/kotlin-telegrambots/build.yml?branch=main)](https://github.com/your-username/kotlin-telegrambots/actions)
[![Maven Central](https://img.shields.io/maven-central/v/ru.kochkaev.kotlin/telegrambots.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:ru.kochkaev.kotlin%20AND%20a:telegrambots)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A lightweight library that provides high-level, idiomatic Kotlin extensions for the popular Java [TelegramBots](https://github.com/rubenlagus/TelegramBots) library. It transforms the verbose, object-oriented API into clean, concise, and modern Kotlin code.

## Overview

The official `TelegramBots` library is an excellent tool, but its Java-centric design can feel cumbersome in Kotlin. This project solves two major pain points:

1.  **Verbose API Calls**: Instead of manually creating method objects (`new SendMessage(...)`), you can call API methods directly: `bot.sendMessage(...)`.
2.  **Complex Object Creation**: Instead of instantiating and configuring objects (`new ReplyParameters().apply { ... }`), you can use clean builder functions: `replyParameters(...)`.

All asynchronous operations are wrapped in `suspend` functions for seamless integration with Kotlin Coroutines.

## Features

- **High-Level API**: Provides direct, expressive methods like `bot.sendMessage(...)`.
- **Convenient Object Builders**: Offers builder functions like `replyParameters(...)` for creating complex objects.
- **Idiomatic Kotlin**: Replaces `CompletableFuture` with `suspend` functions and utilizes default arguments for optional parameters.
- **Auto-Generated**: Two code generation tasks scan the `TelegramBots` library to ensure full API coverage for both methods and objects.
- **Lightweight & Safe**: Adds no runtime overhead and respects the original library's contracts for required and optional fields.

## Installation

Add the library to your project's dependencies.

#### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.kochkaev:kotlin-telegrambots:1.0.2-6.9.7.1")
}
```

#### Gradle (Groovy DSL)

```groovy
// build.gradle
dependencies {
    implementation 'io.github.kochkaev:kotlin-telegrambots:1.0.2-6.9.7.1'
}
```

#### Maven

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.kochkaev</groupId>
    <artifactId>kotlin-telegrambots</artifactId>
    <version>1.0.2-6.9.7.1</version>
</dependency>
```

## Usage

This library makes your bot code significantly cleaner and more readable.

### 1. Calling API Methods

**Before:**
```kotlin
val message = SendMessage()
message.chatId = "12345"
message.text = "Hello from the other side!"
bot.execute(message)
```

**After (with this library):**
```kotlin
import ru.kochkaev.kotlin.telegrambots.sendMessage // Import the suspend extension

// Clean, direct, non-blocking call
val sentMessage = bot.sendMessage(chatId = "12345", text = "Hello from the coroutine side!")
```

### 2. Creating API Objects

**Before:**
```kotlin
val params = ReplyParameters()
params.messageId = update.message.messageId
params.allowSendingWithoutReply = true
```

**After (with this library):**
```kotlin
import ru.kochkaev.kotlin.telegrambots.replyParameters // Import the builder function

// Create the object with a clean, declarative function
val params = replyParameters(
    messageId = update.message.messageId,
    allowSendingWithoutReply = true
)
```

## How It Works

The magic happens at build time. Two separate Gradle tasks analyze the `telegrambots-meta` library:

1.  **Method Generator**: Scans for `BotApiMethod` subclasses and generates `suspend` extension functions for `AbsSender`.
2.  **Object Generator**: Scans for `BotApiObject` subclasses and generates global builder functions.

Both tasks use a hybrid approach:
- **Reflection** (`org.reflections`) is used to reliably find all relevant classes.
- **Source Code Parsing** (`JavaParser`) is used to read the original `.java` files to determine which fields are mandatory (annotated with `@NonNull`) and which are optional.

This ensures the generated functions have correct, idiomatic Kotlin signatures with non-nullable types for required parameters and nullable types with default `null` values for optional ones.

## Building From Source

To build the project locally, simply run the following command:

```sh
./gradlew build
```

To publish the artifacts to your local Maven repository (`~/.m2/repository`), run:

```sh
./gradlew publishToMavenLocal
```

## Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue. If you want to contribute code, please open a pull request.

## License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE.txt) file for details.
