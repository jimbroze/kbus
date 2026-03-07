// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware01

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import kotlin.time.TimeSource

class TimingMiddleware : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return nextMiddleware(message)
        } finally {
            val duration = mark.elapsedNow()
            println("${message::class.simpleName} took $duration")
        }
    }
}
