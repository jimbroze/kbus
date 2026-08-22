// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults03

import com.jimbroze.kbus.api.result.GenericFailure
import com.jimbroze.kbus.example.fixtures.GenericMessageFailure
import com.jimbroze.kbus.example.fixtures.MyCommand
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus

suspend fun main() {
    val result = bus.execute(MyCommand())

    println(result.mapSuccess { it.length }.getOrNull())
    println(result.mapFailure { GenericMessageFailure(GenericFailure("could not do the thing")) })
    println(result.collapse({ "Value: $it" }, { "Error: ${it.reason.message}" }))
}
