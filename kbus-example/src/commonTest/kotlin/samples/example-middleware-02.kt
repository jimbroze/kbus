// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware02

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.middleware.middleware.LoggingMiddleware
import com.jimbroze.kbus.example.fixtures.DebugLevel
import com.jimbroze.kbus.example.fixtures.InfoLevel
import com.jimbroze.kbus.example.fixtures.ErrorLevel
import com.jimbroze.kbus.example.fixtures.logger

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        LoggingMiddleware(logger, DebugLevel, InfoLevel, ErrorLevel),
    )
)
