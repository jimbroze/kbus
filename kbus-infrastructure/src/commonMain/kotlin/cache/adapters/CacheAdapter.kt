package com.jimbroze.kbus.infrastructure.cache.adapters

import com.jimbroze.kbus.infrastructure.cache.Cache

/**
 * Adapts a generic Cache<K, T> into a strictly typed Cache<K, V>, where V is a subtype of T.
 * Incompatible values are treated as a cache miss. Atomicity is kept intact, but an error will be
 * thrown if the cache is modified incompatibly during operations.
 */
inline fun <K : Any, T : Any, reified V : T> Cache<K, T>.asTypedCache(): Cache<K, V> {
    return object : Cache<K, V> {

        override fun get(key: K): V? {
            return this@asTypedCache.get(key) as? V
        }

        override fun put(key: K, value: V) {
            this@asTypedCache.put(key, value)
        }

        override fun getOrPut(key: K, defaultValue: () -> V): V {
            return when (val existing = this@asTypedCache.get(key)) {
                is V -> existing
                null ->
                    this@asTypedCache.getOrPut(key, defaultValue) as? V
                        ?: error(
                            "Cache value was replaced with an incompatible value during getOrPut"
                        )
                else -> {
                    // Cannot use getOrPut because the returned value is incompatible
                    defaultValue().also {
                        if (!this@asTypedCache.replaceIfMatching(key, existing, it)) {
                            error("Cache value was incompatible and then replaced during getOrPut")
                        }
                    }
                }
            }
        }

        override fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean {
            return this@asTypedCache.replaceIfMatching(key, oldValue, newValue)
        }

        override fun remove(key: K) {
            this@asTypedCache.remove(key)
        }

        override fun removeIfMatching(key: K, value: V): Boolean {
            return this@asTypedCache.removeIfMatching(key, value)
        }
    }
}
