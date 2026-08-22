package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

class CreateUser(val name: String, val email: String) : Command<BusResult<String, MessageFailure>>()

class CreateUserHandler : CommandHandler<CreateUser, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: CreateUser): BusResult<String, MessageFailure> =
        BusResult.success("User ${message.name} created")
}

class GetUser(val id: Int) : Query<BusResult<String, MessageFailure>>()

class GetUserHandler : QueryHandler<GetUser, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: GetUser): BusResult<String, MessageFailure> =
        BusResult.success("User #${message.id}")
}
