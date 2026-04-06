package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalAtomicApi::class)
class IntegrationEventObserverRegistry {
    private val registry =
        AtomicReference<Map<KClass<out IntegrationEvent>, ObservableEventMapper<*>>>(emptyMap())

    fun <TEvent : IntegrationEvent> observableFor(eventClass: KClass<TEvent>): Flow<TEvent> {
        while (true) {
            val currentMap = registry.load()
            val existingMapper = currentMap[eventClass]

            if (existingMapper != null) {
                @Suppress("UNCHECKED_CAST")
                return (existingMapper as ObservableEventMapper<TEvent>).events
            }

            val newMapper = ObservableEventMapper<TEvent>()
            val newMap = currentMap + (eventClass to newMapper)

            if (registry.compareAndSet(currentMap, newMap)) {
                return newMapper.events
            }
        }
    }

    suspend fun <TEvent : IntegrationEvent> emit(event: TEvent) {
        @Suppress("UNCHECKED_CAST")
        val mapper = registry.load()[event::class] as? ObservableEventMapper<TEvent>
        mapper?.emit(event)
    }
}
