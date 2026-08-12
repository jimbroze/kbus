package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

data class Box(val value: String)

class FakeDistributedCacheTest :
    CacheContract<String, Box>(createKey = { "key-$it" }, createValue = { Box("value-$it") }) {
    override fun createCache() = FakeDistributedCache<String, Box> { Box(it.value) }

    @Test
    fun `returns a copy rather than the instance that was stored`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        assertNotSame(value, cache.get(key))
    }

    @Test
    fun `matches a value read back from it when replacing conditionally`() {
        val key = createKey(0)
        cache.put(key, createValue(0))
        val readBack = cache.get(key)!!

        assertTrue(cache.replaceIfMatching(key, readBack, createValue(1)))
    }

    @Test
    fun `matches a value read back from it when removing conditionally`() {
        val key = createKey(0)
        cache.put(key, createValue(0))
        val readBack = cache.get(key)!!

        assertTrue(cache.removeIfMatching(key, readBack))
    }
}
