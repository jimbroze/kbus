package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.core.common.Message
import com.jimbroze.kbus.core.common.ResultReturningMessage
import com.jimbroze.kbus.core.messages.command.Command
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.result.BusResult
import com.jimbroze.kbus.core.result.FailureReason
import com.jimbroze.kbus.core.result.KBusResult
import com.jimbroze.kbus.core.result.MessageFailure
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

private const val MILLISECONDS_IN_SECOND = 1000

class MessageHandlerPair<TMessage : Message, TResult : Any?>(
    private val message: TMessage,
    private val handler: MiddlewareHandler<TMessage, TResult>,
) {
    suspend fun handle(): TResult {
        return handler(message)
    }
}

// TODO change to lockaware and have boolean for locking
interface LockingMessage {
    val lockTimeout: Float?
        get() = null
}

interface ResultReturningLockingMessage<TResult : KBusResult> :
    ResultReturningMessage<TResult>, LockingMessage {
    fun busLockedFailure(failure: BusLockedFailure): TResult
}

interface AFailure : FailureReason

sealed interface TestingMessageFailure : MessageFailure {
    companion object {
        fun busLockedFailure(failure: BusLockedFailure): BusResult<Nothing, TestingMessageFailure> =
            BusResult.failure(BusLocked(failure))
    }

    data class A(override val reason: AFailure) : TestingMessageFailure

    data class BusLocked(override val reason: BusLockedFailure) : TestingMessageFailure
}

// TODO move to tests
class LockingMessageImpl(override val lockTimeout: Float? = null) :
    LockingCommand<BusResult<Any, TestingMessageFailure>>,
    Command<BusResult<Any, TestingMessageFailure>>() {
    override val messageType: String = "locking"

    override fun busLockedFailure(
        failure: BusLockedFailure
    ): BusResult<Any, TestingMessageFailure> = TestingMessageFailure.busLockedFailure(failure)
}

// TODO remove interface
interface LockAdjustMessage {
    val lockTimeout: Float
}

interface LockingCommand<TResult : KBusResult> : ResultReturningLockingMessage<TResult>

interface LockingEvent : LockingMessage

class BusLocker(private val clock: Clock, private val defaultTimeout: Float = 5.0f) : Middleware {
    @Volatile var secsToTimeout = defaultTimeout

    @Volatile var lockingCoroutineId: String? = null
    private val queue: MutableList<MessageHandlerPair<*, *>> = mutableListOf()

    val busLocked: Boolean
        get() = lockingCoroutineId != null

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val coroutineId = getCoroutineId()

        println(coroutineId)

        if (busLocked && inLockingCoroutine(coroutineId)) {
            return if (message is ResultReturningLockingMessage<*>) {
                this.postponeHandling(message, nextMiddleware)

                @Suppress("UNCHECKED_CAST")
                message.busLockedFailure(
                    BusLockedFailure(
                        "Cannot handle message as message bus is locked by the same coroutine"
                    )
                ) as TResult
            } else {
                postponeHandling(message, nextMiddleware)
                throw BusLockedException()
            }
        }

        waitForUnlock(message)

        return if (message !is LockingMessage) {
            nextMiddleware(message)
        } else {
            lockAndProcess(coroutineId, message, nextMiddleware)
        }
    }

    private suspend fun <TMessage : Message, TResult> lockAndProcess(
        coroutineId: String,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        lockBus(coroutineId, message as LockingMessage)
        val result = nextMiddleware(message)
        unlockBus()

        handleQueue()
        return result
    }

    private fun inLockingCoroutine(coroutineId: String): Boolean = lockingCoroutineId == coroutineId

    private fun <TMessage : Message, TReturn> postponeHandling(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TReturn>,
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
        val queuedHandlers = queue.toList()
        queue.clear()
        for (messageHandler in queuedHandlers) {
            messageHandler.handle()
        }
    }

    private suspend fun getCoroutineId(): String {
        return coroutineContext[Job]?.toString() ?: ""
    }
}

class BusLockedFailure(override val message: String) : FailureReason

class BusLockedException(
    override val message: String = "The message bus is locked by another coroutine"
) : Exception(message)
