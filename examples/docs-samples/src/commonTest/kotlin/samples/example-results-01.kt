// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults01

import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.MyCommand
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus

suspend fun main() {
    val result: BusResult<String, MessageFailure> = bus.execute(MyCommand())

    when {
        result.isSuccess -> println("Value: ${result.getOrNull()}")
        result.isFailure -> println("Error: ${result.failureOrNull()?.reason?.message}")
    }
}
