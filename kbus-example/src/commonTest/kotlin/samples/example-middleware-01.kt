// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware01

class TimingMiddleware : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val start = clock.now()
        try {
            return nextMiddleware(message)
        } finally {
            val duration = clock.now() - start
            println("${message::class.simpleName} took $duration")
        }
    }
}
