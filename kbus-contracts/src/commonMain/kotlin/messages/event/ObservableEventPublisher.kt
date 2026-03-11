package com.jimbroze.kbus.contracts.messages.event

interface ObservableEventPublisher<TEvent : IntegrationEvent> {
    suspend fun emit(event: TEvent)
}
