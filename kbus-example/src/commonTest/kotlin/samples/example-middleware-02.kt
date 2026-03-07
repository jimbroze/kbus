// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware02

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator
import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.middleware.LogLevel
import com.jimbroze.kbus.core.middleware.middleware.Logger
import com.jimbroze.kbus.core.middleware.middleware.MessageLogger

object DebugLevel : LogLevel { override val level = "DEBUG" }
object InfoLevel : LogLevel { override val level = "INFO" }
object ErrorLevel : LogLevel { override val level = "ERROR" }

val logger = object : Logger {
    override fun log(level: LogLevel, message: String, exception: Throwable?) {
        println("[${level.level}] $message")
    }
}

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        MessageLogger(logger, DebugLevel, InfoLevel, ErrorLevel),
    )
)
