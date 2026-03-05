package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

data class Box(val value: String)

class FakeDistributedCacheTest :
    CacheTestBase<String, Box>(createKey = { "key-$it" }, createValue = { Box("value-$it") }) {
    override fun createCache() = FakeDistributedCache<String, Box> { Box(it.value) }

    @Test
    fun get_returns_a_different_reference_than_what_was_put() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        val retrieved = cache.get(key)

        assertNotSame(value, retrieved, "get should return a copy, not the same reference")
    }

    @Test
    fun replaceIfMatching_succeeds_when_old_value_obtained_via_get() {
        val key = createKey(0)
        val original = createValue(0)
        val replacement = createValue(1)

        cache.put(key, original)
        val copy = cache.get(key)!!

        assertNotSame(original, copy)
        assertTrue(cache.replaceIfMatching(key, copy, replacement))
    }

    @Test
    fun removeIfMatching_succeeds_when_value_obtained_via_get() {
        val key = createKey(0)
        val original = createValue(0)

        cache.put(key, original)
        val copy = cache.get(key)!!

        assertNotSame(original, copy)
        assertTrue(cache.removeIfMatching(key, copy))
    }
}
