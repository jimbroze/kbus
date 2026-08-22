package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.api.messages.event.IntegrationEvent

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()
