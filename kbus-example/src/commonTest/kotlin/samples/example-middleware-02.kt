// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware02

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        BusLockingMiddleware(Clock.System),
        MessageLogger(logger, LogLevel.DEBUG, LogLevel.INFO, LogLevel.ERROR),
    )
)
