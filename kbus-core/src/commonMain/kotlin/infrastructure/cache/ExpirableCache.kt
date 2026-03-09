package com.jimbroze.kbus.core.infrastructure.cache

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

        return if (cachedValue.isExpired(now)) {
            delegate.remove(key)
            null
        } else {
            cachedValue.value
        }
    }

    override fun put(key: K, value: V) {
        delegate.put(key, TimestampedValue(value, clock.now(), null))
    }

    override fun getOrPut(key: K, defaultValue: () -> V): V {
        val existing = get(key)
        if (existing != null) return existing

        val value = defaultValue()
        put(key, value)
        return value
    }

    override fun replaceIfMatching(key: K, oldValue: V, newValue: V): Boolean {
        val existing = get(key)
        if (existing == null || existing != oldValue) return false

        put(key, newValue)
        return true
    }

    override fun remove(key: K) {
        delegate.remove(key)
    }

    override fun removeIfMatching(key: K, value: V): Boolean {
        val existing = get(key)
        if (existing == null || existing != value) return false

        remove(key)
        return true
    }

    fun putExpiring(key: K, value: V, timeToLive: Duration) {
        delegate.put(key, TimestampedValue(value, clock.now(), timeToLive))
    }

    fun expiryTime(key: K): Instant? {
        val timestampedValue = delegate.get(key) ?: return null

        return if (timestampedValue.isExpired(clock.now())) {
            delegate.remove(key)
            null
        } else {
            timestampedValue.timeToLive?.let { ttl -> timestampedValue.cachedAt + ttl }
        }
    }
}

internal data class TimestampedValue<V>(
    val value: V,
    val cachedAt: Instant,
    val timeToLive: Duration?,
) {
    fun isExpired(now: Instant): Boolean {
        return timeToLive != null && now - cachedAt > timeToLive
    }
}

internal class DelegateCacheAdapter<K : Any, V : Any>(private val baseCache: Cache<K, Any>) :
    Cache<K, TimestampedValue<V>> {

    @Suppress("UNCHECKED_CAST")
    override fun get(key: K): TimestampedValue<V>? {
        return baseCache.get(key) as? TimestampedValue<V>
    }

    override fun put(key: K, value: TimestampedValue<V>) {
        baseCache.put(key, value)
    }

    override fun getOrPut(key: K, defaultValue: () -> TimestampedValue<V>): TimestampedValue<V> {
        val existing = get(key)
        if (existing != null) return existing

        val value = defaultValue()
        put(key, value)
        return value
    }

    @Suppress("UNCHECKED_CAST")
    override fun replaceIfMatching(
        key: K,
        oldValue: TimestampedValue<V>,
        newValue: TimestampedValue<V>,
    ): Boolean {
        return baseCache.replaceIfMatching(key, oldValue as Any, newValue as Any)
    }

    @Suppress("UNCHECKED_CAST")
    override fun removeIfMatching(key: K, value: TimestampedValue<V>): Boolean {
        return baseCache.removeIfMatching(key, value as Any)
    }

    override fun remove(key: K) {
        baseCache.remove(key)
    }
}
