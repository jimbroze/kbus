package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

fun <K : Any, V : Any> expirableCache(
    baseCache: Cache<K, Any>,
    clock: Clock,
): ExpirableCache<K, V> {
    val bridgedDelegate = DelegateCacheAdapter<K, V>(baseCache)

    return ExpirableCache(bridgedDelegate, clock)
}

class ExpirableCache<K : Any, V : Any>
internal constructor(
    private val delegate: Cache<K, TimestampedValue<V>>,
    private val clock: Clock,
) : Cache<K, V> {
    override fun get(key: K): V? {
        val cachedValue = delegate.get(key) ?: return null

        val now = clock.now()
        val age = now - cachedValue.cachedAt

        return if (cachedValue.timeToLive == null || age <= cachedValue.timeToLive) {
            cachedValue.value
        } else {
            delegate.remove(key)
            null
        }
    }

    override fun put(key: K, value: V) {
        delegate.put(key, TimestampedValue(value, clock.now(), null))
    }

    fun putExpiring(key: K, value: V, timeToLive: Duration) {
        delegate.put(key, TimestampedValue(value, clock.now(), timeToLive))
    }

    override fun remove(key: K) {
        delegate.remove(key)
    }
}

internal data class TimestampedValue<V>(
    val value: V,
    val cachedAt: Instant,
    val timeToLive: Duration?,
)

internal class DelegateCacheAdapter<K : Any, V : Any>(private val baseCache: Cache<K, Any>) :
    Cache<K, TimestampedValue<V>> {

    @Suppress("UNCHECKED_CAST")
    override fun get(key: K): TimestampedValue<V>? {
        return baseCache.get(key) as? TimestampedValue<V>
    }

    override fun put(key: K, value: TimestampedValue<V>) {
        baseCache.put(key, value)
    }

    override fun remove(key: K) {
        baseCache.remove(key)
    }
}
