// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults01

val result: BusResult<String, MessageFailure> = bus.execute(myCommand)

when {
    result.isSuccess -> println("Value: ${result.getOrNull()}")
    result.isFailure -> println("Error: ${result.failureOrNull()?.reason?.message}")
}
