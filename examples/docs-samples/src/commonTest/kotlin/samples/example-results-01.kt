// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleResults01

import com.jimbroze.kbus.api.result.MessageFailure

sealed interface CancelSubscriptionFailure : MessageFailure {
    data class NotFound(val subscriptionId: String) : CancelSubscriptionFailure
    data class AlreadyCancelled(val subscriptionId: String) : CancelSubscriptionFailure
}
