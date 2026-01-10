package io.github.kochkaev.kotlin.telegrambots.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.util.WeakHashMap
import kotlin.coroutines.EmptyCoroutineContext

// --- Lazy get me ---

private val clientMe = WeakHashMap<TelegramClient, User>()

val TelegramClient.me: User
    get() = clientMe.getOrPut(this) { execute(GetMe()) }

// --- Session Management ---

private val clientSessions = WeakHashMap<TelegramClient, Job>()

val TelegramClient.session: Job?
    get() = clientSessions[this]

fun TelegramClient.startSession(job: Job) {
    if (session?.isActive == true) {
        throw IllegalStateException("Bot session is already active. Cancel the previous session before starting a new one.")
    }
    clientSessions[this] = job
}

// --- Update Flow Management ---

private val clientFlows = WeakHashMap<TelegramClient, MutableSharedFlow<Update>>()

private val TelegramClient.mutableUpdates: MutableSharedFlow<Update>
    get() = clientFlows.getOrPut(this) {
        MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

fun TelegramClient.emitUpdate(update: Update) {
    mutableUpdates.tryEmit(update)
}

val TelegramClient.updates: Flow<Update>
    get() = mutableUpdates.asSharedFlow()


// --- Handlers DSL ---

open class HandlersScope(
    val client: TelegramClient,
    var sourceFlow: Flow<Update>,
    val coroutine: CoroutineScope
)

/**
 * Registers a handler for all updates.
 */
fun HandlersScope.onUpdate(handler: suspend TelegramClient.(Update) -> Unit) {
    coroutine.launch {
        sourceFlow.collect { with(client) { handler(it) } }
    }
}

/**
 * Registers a handler for commands.
 * A command is a message that starts with "/".
 */
fun HandlersScope.onCommand(command: String, handler: suspend TelegramClient.(Message) -> Unit) {
    coroutine.launch {
        val botUsername = client.me.userName
        val cmdRegex = if (botUsername != null) {
            Regex("^/$command(@$botUsername)?(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)
        } else {
            Regex("^/$command(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)
        }

        sourceFlow.filter {
            it.hasMessage() && it.message.isCommand && it.message.text.matches(cmdRegex)
        }.collect { with(client) { handler(it.message) } }
    }
}

fun HandlersScope.map(wrapper: suspend TelegramClient.(Update) -> Update) {
    sourceFlow = sourceFlow.map { with(client) { wrapper(it) } }
}

fun HandlersScope.mapNotNull(wrapper: suspend TelegramClient.(Update) -> Update?) {
    sourceFlow = sourceFlow.mapNotNull { with(client) { wrapper(it) } }
}

fun HandlersScope.filter(wrapper: suspend TelegramClient.(Update) -> Boolean) {
    sourceFlow = sourceFlow.filter { with(client) { wrapper(it) } }
}

fun HandlersScope.wrap(wrapper: TelegramClient.(Flow<Update>) -> Flow<Update>) {
    sourceFlow = with(client) { wrapper(sourceFlow) }
}

private val clientHandlersScopes = WeakHashMap<TelegramClient, HandlersScope>()

val TelegramClient.handlersScope: HandlersScope
    get() = clientHandlersScopes.getOrPut(this) { HandlersScope(this, updates, CoroutineScope(Job())) }
