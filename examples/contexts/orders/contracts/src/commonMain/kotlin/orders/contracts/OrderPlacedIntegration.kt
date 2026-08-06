package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()
