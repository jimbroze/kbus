// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleIntegrationEvents02

import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.RegisterUser
import com.jimbroze.kbus.example.fixtures.UserRegistered

class RegisterUserHandler(private val integrationEventPublisher: IntegrationEventPublisher) :
    CommandHandler<RegisterUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: RegisterUser): BusResult<String, MessageFailure> {
        val userId = "generated-id"

        integrationEventPublisher.publish(listOf(UserRegistered(userId)))

        return BusResult.success(userId)
    }
}
