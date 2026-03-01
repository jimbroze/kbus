package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Abstract test base for [Cache] implementations. Subclass this and implement [createCache] to
 * fully test a cache implementation.
 */
abstract class CacheTestBase<K : Any, V : Any>(
    protected val createKey: (Int) -> K,
    protected val createValue: (Int) -> V,
) {
    protected lateinit var cache: Cache<K, V>

    /** Create a fresh, empty cache instance for each test. */
    abstract fun createCache(): Cache<K, V>

    @BeforeTest
    fun setUp() {
        cache = createCache()
    }

    @Test
    fun get_on_empty_cache_returns_null() {
        assertNull(cache.get(createKey(0)))
    }

    @Test
    fun get_returns_value_that_was_put() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        assertEquals(value, cache.get(key))
    }

    @Test
    fun put_with_existing_key_replaces_previous_value() {
        val key = createKey(0)
        val firstValue = createValue(0)
        val secondValue = createValue(1)

        cache.put(key, firstValue)
        cache.put(key, secondValue)

        assertEquals(secondValue, cache.get(key))
    }

    @Test
    fun get_returns_null_after_entry_is_removed() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.remove(key)

        assertNull(cache.get(key))
    }

    @Test
    fun remove_with_nonexistent_key_does_not_throw() {
        cache.remove(createKey(0))
    }

    @Test
    fun put_with_different_keys_stores_each_value_independently() {
        val key1 = createKey(0)
        val key2 = createKey(1)
        val value1 = createValue(0)
        val value2 = createValue(1)

        cache.put(key1, value1)
        cache.put(key2, value2)

        assertEquals(value1, cache.get(key1))
        assertEquals(value2, cache.get(key2))
    }

    @Test
    fun remove_only_deletes_the_specified_key() {
        val key1 = createKey(0)
        val key2 = createKey(1)
        val value1 = createValue(0)
        val value2 = createValue(1)

        cache.put(key1, value1)
        cache.put(key2, value2)
        cache.remove(key1)

        assertNull(cache.get(key1))
        assertEquals(value2, cache.get(key2))
    }

    @Test
    fun put_after_remove_restores_entry_with_new_value() {
        val key = createKey(0)
        val firstValue = createValue(0)
        val secondValue = createValue(1)

        cache.put(key, firstValue)
        cache.remove(key)
        cache.put(key, secondValue)

        assertEquals(secondValue, cache.get(key))
    }

    @Test
    fun get_does_not_consume_or_remove_the_entry() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.get(key)

        assertEquals(value, cache.get(key))
    }

    @Test
    fun cache_stores_and_retrieves_100_entries_correctly() {
        val count = 100
        val keys = (0 until count).map { createKey(it) }
        val values = (0 until count).map { createValue(it) }

        keys.zip(values).forEach { (k, v) -> cache.put(k, v) }

        keys.zip(values).forEach { (k, v) -> assertEquals(v, cache.get(k)) }
    }
}
