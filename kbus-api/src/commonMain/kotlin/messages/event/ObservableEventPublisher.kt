package com.jimbroze.kbus.api.messages.event

interface ObservableEventPublisher<TEvent : IntegrationEvent> {
    suspend fun emit(event: TEvent)
}
