// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults04

import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.example.fixtures.CancelSubscriptionFailure

val success = BusResult.success("Cancelled sub-42")
val failure = BusResult.failure(CancelSubscriptionFailure.NotFound("sub-42"))
