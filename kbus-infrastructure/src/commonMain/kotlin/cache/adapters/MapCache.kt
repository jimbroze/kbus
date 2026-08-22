package com.jimbroze.kbus.infrastructure.cache.adapters

import com.jimbroze.kbus.infrastructure.cache.Cache

class MapCache<K : Any, V : Any> : Cache<K, V> {
    val cacheMap = mutableMapOf<K, V>()

    override fun get(key: K): V? {
        return cacheMap[key]
    }

    override fun put(key: K, value: V) {
        cacheMap[key] = value
    }

    override fun getOrPut(key: K, defaultValue: () -> V): V {
        return cacheMap.getOrPut(key, defaultValue)
    }

    override fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean {
        if (cacheMap[key] != oldValue) return false
        cacheMap[key] = newValue
        return true
    }

    override fun remove(key: K) {
        cacheMap.remove(key)
    }

    override fun removeIfMatching(key: K, value: V): Boolean {
        if (cacheMap[key] != value) return false
        cacheMap.remove(key)
        return true
    }
}
