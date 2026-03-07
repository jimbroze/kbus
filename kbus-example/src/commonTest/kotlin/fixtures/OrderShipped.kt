package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.domain.DomainEvent

class OrderShipped(val orderId: String) : DomainEvent()
