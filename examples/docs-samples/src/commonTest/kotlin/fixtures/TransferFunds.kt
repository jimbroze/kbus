package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

class TransferFunds(val from: String, val to: String, val amount: Int) :
    Command<BusResult<Unit, MessageFailure>>()
