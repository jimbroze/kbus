package com.jimbroze.kbus.contracts.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult
import kotlin.time.Duration

interface LockAwareMessage : Message {
    val shouldLockBus: Boolean
    val lockChannelKey: String?
        get() = null

    val lockTimeoutOverride: Duration?
        get() = null

    val ignoreLockOnTimeoutOverride: Boolean?
        get() = null
}

interface ResultReturningLockAwareMessage<TResult : KBusResult> :
    ResultReturningMessage<TResult>, LockAwareMessage {
    fun busLockedFailure(failure: BusLockedFailure): TResult
}

interface LockingCommand<TResult : KBusResult> : ResultReturningLockAwareMessage<TResult> {
    override val shouldLockBus: Boolean
        get() = true
}

class BusLockedFailure(override val message: String) : FailureReason

class BusLockedException(override val message: String) : Exception(message)
