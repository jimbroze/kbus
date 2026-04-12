package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.Cache

/**
 * A [com.jimbroze.kbus.core.infrastructure.cache.Cache] test double that simulates non-in-memory
 * (e.g. Redis, database) behavior: every [get] and [getOrPut] call returns a *copy* of the stored
 * value, so callers never hold a reference to the cached object.
 */
class FakeDistributedCache<K : Any, V : Any>(private val copy: (V) -> V) : Cache<K, V> {
    private val map = mutableMapOf<K, V>()

    override fun get(key: K): V? {
        return map[key]?.let { copy(it) }
    }

    override fun put(key: K, value: V) {
        map[key] = value
    }

    override fun getOrPut(key: K, defaultValue: () -> V): V {
        val existing = map[key]
        if (existing != null) return copy(existing)
        val value = defaultValue()
        map[key] = value
        return copy(value)
    }

    override fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean {
        if (map[key] != oldValue) return false
        map[key] = newValue
        return true
    }

    override fun remove(key: K) {
        map.remove(key)
    }

    override fun removeIfMatching(key: K, value: V): Boolean {
        if (map[key] != value) return false
        map.remove(key)
        return true
    }
}
