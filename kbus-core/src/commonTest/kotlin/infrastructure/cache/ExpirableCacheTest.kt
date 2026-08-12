@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.core.infrastructure.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ExpirableCacheTest :
    CacheContract<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {

    private val fixedClock = FixedClock(Instant.parse("2016-02-15T12:00:00Z"))

    private val expirableCache: ExpirableCache<String, String>
        get() = cache as ExpirableCache<String, String>

    override fun createCache(): Cache<String, String> {
        fixedClock.nowInstant = Instant.parse("2016-02-15T12:00:00Z")
        return ExpirableCache(MapCache(), fixedClock)
    }

    @Test
    fun `returns the value before its lifetime has elapsed`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 4.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `returns the value at the instant its lifetime elapses`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 5.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `returns null once the lifetime has elapsed`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 6.minutes

        assertNull(cache.get("key"))
    }

    @Test
    fun `evicts an expired entry when it is read`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 6.minutes
        cache.get("key")

        // Winding back past the expiry distinguishes eviction from a read-time filter.
        fixedClock.nowInstant -= 6.minutes
        assertNull(cache.get("key"))
    }

    @Test
    fun `keeps an entry stored without a lifetime indefinitely`() {
        cache.put("key", "value")

        fixedClock.nowInstant += 999.minutes

        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `applies the new lifetime when an entry is stored again`() {
        expirableCache.putExpiring("key", "value1", 2.minutes)
        expirableCache.putExpiring("key", "value2", 10.minutes)

        fixedClock.nowInstant += 5.minutes

        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun `clears the lifetime when an entry is stored again without one`() {
        expirableCache.putExpiring("key", "value1", 2.minutes)
        cache.put("key", "value2")

        fixedClock.nowInstant += 999.minutes

        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun `expires each key on its own lifetime`() {
        expirableCache.putExpiring("short", "s", 1.minutes)
        expirableCache.putExpiring("long", "l", 10.minutes)

        fixedClock.nowInstant += 5.minutes

        assertNull(cache.get("short"))
        assertEquals("l", cache.get("long"))
    }

    @Test
    fun `returns the default when getting or putting an expired entry`() {
        expirableCache.putExpiring("key", "old", 5.minutes)

        fixedClock.nowInstant += 6.minutes

        val result = cache.getOrPut("key") { "new" }

        assertEquals("new", result)
    }

    @Test
    fun `returns the stored value when getting or putting an unexpired entry`() {
        expirableCache.putExpiring("key", "old", 5.minutes)

        fixedClock.nowInstant += 4.minutes

        val result = cache.getOrPut("key") { "new" }

        assertEquals("old", result)
    }

    @Test
    fun `refuses a conditional replacement of an expired entry`() {
        expirableCache.putExpiring("key", "old", 5.minutes)

        fixedClock.nowInstant += 6.minutes

        val result = cache.replaceIfMatching("key", "old", "new")

        assertFalse(result)
        assertNull(cache.get("key"))
    }

    @Test
    fun `refuses a conditional removal of an expired entry`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 6.minutes

        val result = cache.removeIfMatching("key", "value")

        assertFalse(result)
    }

    @Test
    fun `replaces an unexpired entry conditionally`() {
        expirableCache.putExpiring("key", "old", 5.minutes)

        fixedClock.nowInstant += 4.minutes

        assertTrue(cache.replaceIfMatching("key", "old", "new"))
        assertEquals("new", cache.get("key"))
    }

    @Test
    fun `removes an unexpired entry conditionally`() {
        expirableCache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 4.minutes

        assertTrue(cache.removeIfMatching("key", "value"))
        assertNull(cache.get("key"))
    }

    @Test
    fun `expires an entry given no lifetime at all`() {
        expirableCache.putExpiring("key", "value", 0.seconds)

        fixedClock.nowInstant += 1.seconds

        assertNull(cache.get("key"))
    }
}

class ExpirableCacheFactoryTest {
    @Test
    fun `builds a cache that returns the values stored in it`() {
        val baseCache = MapCache<String, Any>()
        val cache: Cache<String, String> =
            expirableCache(baseCache, FixedClock(Instant.parse("2016-02-15T12:00:00Z")))

        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `builds a cache that expires entries once their lifetime elapses`() {
        val baseCache = MapCache<String, Any>()
        val fixedClock = FixedClock(Instant.parse("2016-02-15T12:00:00Z"))
        val cache = expirableCache<String, String>(baseCache, fixedClock)

        cache.putExpiring("key", "value", 5.minutes)

        fixedClock.nowInstant += 10.minutes

        assertNull(cache.get("key"))
    }
}

private class FixedClock(var nowInstant: Instant) : Clock {
    override fun now(): Instant = nowInstant
}
