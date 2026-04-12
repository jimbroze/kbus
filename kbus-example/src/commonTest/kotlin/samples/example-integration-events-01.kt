// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleIntegrationEvents01

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler

class UserRegistered(val userId: String) : IntegrationEvent()

class SyncToExternalCRM :
    IntegrationEventHandler<UserRegistered> {

    override suspend fun handle(message: UserRegistered) {
        // Sync to external system...
    }
}
