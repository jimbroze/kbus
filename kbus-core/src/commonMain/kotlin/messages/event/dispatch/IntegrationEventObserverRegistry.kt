package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import kotlin.reflect.KClass
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.flow.Flow

class IntegrationEventObserverRegistry {
    private val registry =
        atomic<Map<KClass<out IntegrationEvent>, ObservableEventMapper<*>>>(emptyMap())

    fun <TEvent : IntegrationEvent> observableFor(eventClass: KClass<TEvent>): Flow<TEvent> {
        var targetMapper: ObservableEventMapper<TEvent>? = null

        registry.update { currentMap ->
            val existing = currentMap[eventClass]
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                targetMapper = existing as ObservableEventMapper<TEvent>
                currentMap
            } else {
                val newMapper = ObservableEventMapper<TEvent>()
                targetMapper = newMapper
                currentMap + (eventClass to newMapper)
            }
        }

        return targetMapper!!.events
    }

    suspend fun <TEvent : IntegrationEvent> emit(event: TEvent) {
        @Suppress("UNCHECKED_CAST")
        val mapper = registry.value[event::class] as? ObservableEventMapper<TEvent>
        mapper?.emit(event)
    }
}
