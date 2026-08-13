package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

class RegisterUser(val name: String) : Command<BusResult<String, MessageFailure>>()

class UserRegistered(val userId: String) : IntegrationEvent()
