// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMessages01

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure

// A command that returns a String result
class CreateUser(val name: String, val email: String) :
    Command<BusResult<String, MessageFailure>>()

class CreateUserHandler :
    CommandHandler<CreateUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: CreateUser): BusResult<String, MessageFailure> {
        // Create the user...
        return BusResult.success("User ${message.name} created")
    }
}

// A query that returns a String result
class GetUser(val id: Int) :
    Query<BusResult<String, MessageFailure>>()

class GetUserHandler :
    QueryHandler<GetUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: GetUser): BusResult<String, MessageFailure> {
        // Look up the user...
        return BusResult.success("User #${message.id}")
    }
}
