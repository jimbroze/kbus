package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.middleware.BusLockedFailure
import com.jimbroze.kbus.contracts.middleware.LockingCommand
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.failure
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.DefaultMiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.delay

class TestFailure(override val reason: FailureReason) : MessageFailure

/** Returns the current time mark. Not lock-aware (no LockingCommand). */
open class TimeReturnCommand : Command<BusResult<ValueTimeMark, MessageFailure>>()

/** Lock-aware variant of [TimeReturnCommand]; returns a [TestFailure] when the bus is locked. */
class LockAwareTimeReturnCommand(override val lockChannelKey: String? = null) :
    TimeReturnCommand(), LockingCommand<BusResult<ValueTimeMark, MessageFailure>> {
    override fun busLockedFailure(
        failure: BusLockedFailure
    ): BusResult<ValueTimeMark, MessageFailure> = failure(TestFailure(failure))
}

class TimeReturnCommandHandler :
    CommandHandler<TimeReturnCommand, BusResult<ValueTimeMark, MessageFailure>>() {
    override suspend fun handle(
        message: TimeReturnCommand
    ): BusResult<ValueTimeMark, MessageFailure> = success(TimeSource.Monotonic.markNow())
}

/**
 * Dispatches [internalCommand] through the locker during handling, creating a nested/reentrant lock
 * scenario. Returns a map with "pre-nest", "nest" (inner result), and "post-nest" timestamps.
 */
class NestingLockCommand(
    val internalCommand: TimeReturnCommand,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class NestingLockCommandHandler(private val locker: LockingMiddleware) :
    CommandHandler<NestingLockCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: NestingLockCommand): BusResult<Any, MessageFailure> {
        val preNestTime = TimeSource.Monotonic.markNow()

        val result =
            locker.handle(message.internalCommand, DefaultMiddlewareInvocationContext) {
                c: TimeReturnCommand ->
                TimeReturnCommandHandler().handle(c)
            }

        val postNestTime = TimeSource.Monotonic.markNow()

        return success(
            mapOf("pre-nest" to preNestTime, "nest" to result, "post-nest" to postNestTime)
        )
    }
}

/** Lock-aware command that delays for [sleepFor] before returning. Used to hold a lock. */
class LockingSleepCommand(
    val sleepFor: Duration,
    val messageData: String,
    override val lockTimeoutOverride: Duration? = null,
    override val ignoreLockOnTimeoutOverride: Boolean? = null,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class LockingSleepCommandHandler :
    CommandHandler<LockingSleepCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: LockingSleepCommand): BusResult<Any, MessageFailure> {
        delay(message.sleepFor)
        return success(message.messageData)
    }
}

/** Non-locking command that delays for [sleepFor]. Does not acquire or interact with the lock. */
class SleepCommand(val sleepFor: Duration) : Command<BusResult<Unit, MessageFailure>>()

class SleepCommandHandler : CommandHandler<SleepCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: SleepCommand): BusResult<Unit, MessageFailure> {
        delay(message.sleepFor)
        return success(Unit)
    }
}

/**
 * Lock-aware command that completes instantly. Supports configurable lock overrides (timeout,
 * ignoreLockOnTimeout, channel key) for testing the override chain.
 */
class ConfigurableLockingCommand(
    val messageData: String,
    override val lockTimeoutOverride: Duration? = null,
    override val ignoreLockOnTimeoutOverride: Boolean? = null,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class ConfigurableLockingCommandHandler :
    CommandHandler<ConfigurableLockingCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: ConfigurableLockingCommand
    ): BusResult<Any, MessageFailure> {
        return success(message.messageData)
    }
}
