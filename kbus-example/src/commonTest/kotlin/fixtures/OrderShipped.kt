package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.domain.event.DomainEvent

class OrderShipped(val orderId: String) : DomainEvent()
