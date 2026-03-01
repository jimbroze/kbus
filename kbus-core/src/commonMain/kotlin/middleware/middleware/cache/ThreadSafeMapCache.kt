package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ThreadSafeMapCache<K : Any, V : Any> : Cache<K, V> {
    private val cacheRef = AtomicReference<Map<K, V>>(emptyMap())

    override fun get(key: K): V? {
        // Reads are completely lock-free and extremely fast
        return cacheRef.load()[key]
    }

    override fun put(key: K, value: V) {
        // CAS loop: safely updates the map even under high contention
        while (true) {
            val currentMap = cacheRef.load()
            val newMap = currentMap + (key to value)
            if (cacheRef.compareAndSet(currentMap, newMap)) {
                break
            }
        }
    }

    override fun remove(key: K) {
        while (true) {
            val currentMap = cacheRef.load()
            val newMap = currentMap - key
            if (cacheRef.compareAndSet(currentMap, newMap)) {
                break
            }
        }
    }
}
