package com.jimbroze.kbus.infrastructure.logging

interface LogLevel {
    val level: String
}

interface Logger {
    fun log(level: LogLevel, message: String, exception: Throwable?)
}
