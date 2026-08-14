package com.jimbroze.kbus.infrastructure.cache.adapters

import com.jimbroze.kbus.infrastructure.cache.Cache
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ThreadSafeMapCache<K : Any, V : Any> : Cache<K, V> {
    private val cacheRef = AtomicReference<Map<K, V>>(emptyMap())

    override fun get(key: K): V? {
        return cacheRef.load()[key]
    }

    override fun put(key: K, value: V) {
        while (true) {
            val currentMap = cacheRef.load()
            val newMap = currentMap + (key to value)
            if (cacheRef.compareAndSet(currentMap, newMap)) {
                break
            }
        }
    }

    override fun getOrPut(key: K, defaultValue: () -> V): V {
        while (true) {
            val currentMap = cacheRef.load()
            currentMap[key]?.let {
                return it
            }
            val value = defaultValue()
            val newMap = currentMap + (key to value)
            if (cacheRef.compareAndSet(currentMap, newMap)) {
                return value
            }
        }
    }

    override fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean {
        while (true) {
            val currentMap = cacheRef.load()
            if (currentMap[key] != oldValue) return false
            val newMap = currentMap + (key to newValue)
            if (cacheRef.compareAndSet(currentMap, newMap)) return true
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

    override fun removeIfMatching(key: K, value: V): Boolean {
        while (true) {
            val currentMap = cacheRef.load()
            if (currentMap[key] != value) return false
            val newMap = currentMap - key
            if (cacheRef.compareAndSet(currentMap, newMap)) return true
        }
    }
}
