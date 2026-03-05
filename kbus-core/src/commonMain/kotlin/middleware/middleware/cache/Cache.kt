package com.jimbroze.kbus.core.middleware.middleware.cache

interface Cache<K : Any, V : Any> {
    /** Returns the value for the given key, or null if it's a cache miss. */
    fun get(key: K): V?

    /** Caches the value for the given key. */
    fun put(key: K, value: V)

    fun getOrPut(key: K, defaultValue: () -> V): V

    fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean

    /** Removes the entry for the given key. */
    fun remove(key: K)

    fun removeIfMatching(key: K, value: V): Boolean

    //    suspend fun putIfAbsent(key: K, value: V): Boolean
}
