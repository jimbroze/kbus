// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults02

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.example.fixtures.GenericMessageFailure

val success = BusResult.success("value")
val failure = BusResult.failure(GenericMessageFailure(GenericFailure("Something went wrong")))
