package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExpirableCacheTest :
    CacheTestBase<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {

    private val fixedClock = FixedClock(Instant.parse("2016-02-15T12:00:00Z"))

    override fun createCache(): Cache<String, String> {
        fixedClock.now = Instant.parse("2016-02-15T12:00:00Z")
        return ExpirableCache(MapCache(), fixedClock)
    }

    @Test
    fun putExpiring_returns_value_before_expiry() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.now += 4.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun putExpiring_returns_value_at_exact_expiry() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.now += 5.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun putExpiring_returns_null_after_expiry() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.now += 6.minutes

        assertNull(cache.get("key"))
    }

    @Test
    fun putExpiring_removes_entry_on_expired_get() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.now += 6.minutes
        cache.get("key") // triggers removal

        // Reset clock — entry should still be gone
        fixedClock.now -= 6.minutes
        assertNull(cache.get("key"))
    }

    @Test
    fun put_without_ttl_never_expires() {
        cache.put("key", "value")

        fixedClock.now += 999.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun putExpiring_replaces_previous_ttl() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value1", 2.minutes)
        expirableCache.putExpiring("key", "value2", 10.minutes)

        fixedClock.now += 5.minutes

        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun put_replaces_expiring_entry_with_non_expiring() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value1", 2.minutes)
        cache.put("key", "value2")

        fixedClock.now += 999.minutes

        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun different_keys_expire_independently() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("short", "s", 1.minutes)
        expirableCache.putExpiring("long", "l", 10.minutes)

        fixedClock.now += 5.minutes

        assertNull(cache.get("short"))
        assertEquals("l", cache.get("long"))
    }

    @Test
    fun putExpiring_with_zero_duration_expires_immediately() {
        val expirableCache = cache as ExpirableCache<String, String>
        expirableCache.putExpiring("key", "value", 0.seconds)

        fixedClock.now += 1.seconds

        assertNull(cache.get("key"))
    }
}

class ExpirableCacheFactoryTest {
    @Test
    fun expirableCache_factory_creates_working_cache() {
        val baseCache = MapCache<String, Any>()
        val cache: Cache<String, String> =
            expirableCache(baseCache, FixedClock(Instant.parse("2016-02-15T12:00:00Z")))

        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun expirableCache_factory_creates_working_cache_with_expiry() {
        val baseCache = MapCache<String, Any>()
        val fixedClock = FixedClock(Instant.parse("2016-02-15T12:00:00Z"))
        val cache = expirableCache<String, String>(baseCache, fixedClock)

        cache.putExpiring("key", "value", 5.minutes)

        fixedClock.now += 10.minutes

        assertNull(cache.get("key"))
    }
}

private class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
