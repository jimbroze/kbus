package com.jimbroze.kbus.core.middleware.middleware.cache

interface Cache<K : Any, V : Any> {
    /** Returns the value for the given key, or null if it's a cache miss. */
    fun get(key: K): V?

    /** Caches the value for the given key. */
    fun put(key: K, value: V)

    /** Removes the entry for the given key. */
    fun remove(key: K)
}
