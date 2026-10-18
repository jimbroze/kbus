// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults05

import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.CancelSubscription
import com.jimbroze.kbus.example.fixtures.CancelSubscriptionFailure
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus

sealed interface ChangePlanFailure : MessageFailure {
    data class CancellationFailed(val cause: CancelSubscriptionFailure) : ChangePlanFailure
}

suspend fun main() {
    val result = bus.execute(CancelSubscription("sub-42"))

    println(result.mapSuccess { it.length }.getOrNull())
    println(result.mapFailure { ChangePlanFailure.CancellationFailed(cause = it) })
    println(result.collapse({ "Value: $it" }, { "Error: $it" }))
}
