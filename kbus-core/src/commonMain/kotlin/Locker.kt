package com.jimbroze.kbus.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext

class MessageHandlerPair<TMessage : Message>(
    private val message: TMessage,
    private val handler: MiddlewareHandler<TMessage>,
) {
    suspend fun handle(): Any? {
        return handler(message)
    }
}

interface LockingMessage {
    val lockTimeout: Float?
        get() = null
}

interface LockAdjustMessage {
    val lockTimeout: Float
}

interface LockingCommand : LockingMessage

interface LockingEvent : LockingMessage

class BusLocker(private val clock: Clock, private val defaultTimeout: Float = 5.0f) : Middleware {
    @Volatile
    var secsToTimeout = defaultTimeout

    @Volatile
    var lockingCoroutineId: String? = null
    private val queue: MutableList<MessageHandlerPair<*>> = mutableListOf()

    val busLocked: Boolean
        get() = lockingCoroutineId != null

    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
        val coroutineId = getCoroutineId()

        println(coroutineId)

        if (busLocked) {
            if (inLockingCoroutine(coroutineId)) {
                postponeHandling(message, nextMiddleware)
                return BusResult.failure<Unit, BusLockedFailure>(BusLockedFailure(
                    "Cannot handle message as message bus is locked by the same coroutine"
                ))
            }
        }

        waitForUnlock(message)

        if (message !is LockingMessage) return nextMiddleware(message)

        lockBus(coroutineId, message)
        val result = nextMiddleware(message)
        unlockBus()

        handleQueue()
        return result
    }

    private fun inLockingCoroutine(coroutineId: String): Boolean = lockingCoroutineId == coroutineId

    private fun <TMessage : Message> postponeHandling(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ) {
        queue.add(MessageHandlerPair(message, nextMiddleware))
    }

    private suspend fun waitForUnlock(message: Message) {
        val currentTimeout = (message as? LockAdjustMessage)?.lockTimeout ?: secsToTimeout
        val timeout = clock.now().plus((currentTimeout * 1000).toInt(), DateTimeUnit.MILLISECOND)

        while (busLocked && clock.now() <= timeout) {
            // FIXME why does yield() not work?
            delay(1)
        }
    }

    private fun lockBus(
        threadId: String,
        message: LockingMessage,
    ) {
        secsToTimeout = message.lockTimeout ?: defaultTimeout
        lockingCoroutineId = threadId
    }

    private fun unlockBus() {
        lockingCoroutineId = null
    }

    private suspend fun handleQueue() {
        for (messageHandler in queue) {
            messageHandler.handle()
        }
    }

    private suspend fun getCoroutineId(): String {
        return coroutineContext[Job]?.toString() ?: ""
    }
}

class BusLockedFailure(message: String? = null) : ResultFailure(message)

