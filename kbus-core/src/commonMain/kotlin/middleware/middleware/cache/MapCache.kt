package com.jimbroze.kbus.core.middleware.middleware.cache

class MapCache<K : Any, V : Any> : Cache<K, V> {
    val cacheMap = mutableMapOf<K, V>()

    override fun get(key: K): V? {
        return cacheMap[key]
    }

    override fun put(key: K, value: V) {
        cacheMap[key] = value
    }

    override fun remove(key: K) {
        cacheMap.remove(key)
    }
}
