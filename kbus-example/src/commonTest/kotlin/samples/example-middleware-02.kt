// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMiddleware02

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator
import com.jimbroze.kbus.core.middleware.middleware.MessageLogger
import com.jimbroze.kbus.example.fixtures.DebugLevel
import com.jimbroze.kbus.example.fixtures.InfoLevel
import com.jimbroze.kbus.example.fixtures.ErrorLevel
import com.jimbroze.kbus.example.fixtures.logger

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        MessageLogger(logger, DebugLevel, InfoLevel, ErrorLevel),
    )
)
