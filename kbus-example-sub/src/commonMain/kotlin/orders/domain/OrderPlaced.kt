package com.jimbroze.kbus.generation.test.orders.domain

import com.jimbroze.kbus.domain.event.DomainEvent

class OrderPlaced(val orderId: String, val customerId: String) : DomainEvent()
