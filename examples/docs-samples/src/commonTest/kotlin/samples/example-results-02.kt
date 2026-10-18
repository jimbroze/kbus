// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults02

import com.jimbroze.kbus.example.fixtures.CancelSubscription
import com.jimbroze.kbus.example.fixtures.CancelSubscriptionFailure
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus

suspend fun main() {
    val result = bus.execute(CancelSubscription("sub-42"))

    when (val failure = result.failureOrNull()) {
        null -> println("Value: ${result.getOrNull()}")
        is CancelSubscriptionFailure.NotFound -> println("No subscription ${failure.subscriptionId}")
        is CancelSubscriptionFailure.AlreadyCancelled -> println("Already cancelled")
    }
}
