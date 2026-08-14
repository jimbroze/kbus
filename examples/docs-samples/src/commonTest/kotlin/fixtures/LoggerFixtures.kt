package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.infrastructure.logging.LogLevel
import com.jimbroze.kbus.infrastructure.logging.Logger

object DebugLevel : LogLevel {
    override val level = "DEBUG"
}

object InfoLevel : LogLevel {
    override val level = "INFO"
}

object ErrorLevel : LogLevel {
    override val level = "ERROR"
}

val logger =
    object : Logger {
        override fun log(level: LogLevel, message: String, exception: Throwable?) {
            println("[${level.level}] $message")
        }
    }
