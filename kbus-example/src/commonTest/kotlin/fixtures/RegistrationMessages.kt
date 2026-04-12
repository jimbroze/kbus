package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

class RegisterUser(val name: String) : Command<BusResult<String, MessageFailure>>()

class UserRegistered(val userId: String) : IntegrationEvent()
