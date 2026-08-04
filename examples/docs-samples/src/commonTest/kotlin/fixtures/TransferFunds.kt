package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

class TransferFunds(val from: String, val to: String, val amount: Int) :
    Command<BusResult<Unit, MessageFailure>>()
