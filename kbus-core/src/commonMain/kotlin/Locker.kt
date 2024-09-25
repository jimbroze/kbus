package com.jimbroze.kbus.core

import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

private const val MILLISECONDS_IN_SECOND = 1000

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
    @Volatile var secsToTimeout = defaultTimeout

    @Volatile var lockingCoroutineId: String? = null
    private val queue: MutableList<MessageHandlerPair<*>> = mutableListOf()

    val busLocked: Boolean
        get() = lockingCoroutineId != null

    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
        val coroutineId = getCoroutineId()

        println(coroutineId)

        if (busLocked && inLockingCoroutine(coroutineId)) {
            return postponeAndFail(message, nextMiddleware)
        }

        waitForUnlock(message)

        return if (message !is LockingMessage) {
            nextMiddleware(message)
        } else {
            lockAndProcess(coroutineId, message, nextMiddleware)
        }
    }

    private suspend fun <TMessage : LockingMessage> lockAndProcess(
        coroutineId: String,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
        lockBus(coroutineId, message)
        val result = nextMiddleware(message)
        unlockBus()

        handleQueue()
        return result
    }

    private fun <TMessage : Message> postponeAndFail(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): BusResult<Unit, BusLockedFailure> {
        postponeHandling(message, nextMiddleware)

        return BusResult.failure(
            BusLockedFailure("Cannot handle message as message bus is locked by the same coroutine")
        )
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
        val timeout =
            clock
                .now()
                .plus((currentTimeout * MILLISECONDS_IN_SECOND).toInt(), DateTimeUnit.MILLISECOND)

        while (busLocked && clock.now() <= timeout) {
            // FIXME why does yield() not work?
            delay(1)
        }
    }

    private fun lockBus(threadId: String, message: LockingMessage) {
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

class BusLockedFailure(message: String? = null) : FailureReason(message)
