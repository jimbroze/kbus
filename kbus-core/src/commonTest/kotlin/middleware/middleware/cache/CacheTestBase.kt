package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.Cache
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abstract test base for [com.jimbroze.kbus.core.infrastructure.cache.Cache] implementations.
 * Subclass this and implement [createCache] to fully test a cache implementation.
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

    // getOrPut

    @Test
    fun getOrPut_returns_existing_value_when_key_is_present() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        val result = cache.getOrPut(key) { createValue(1) }

        assertEquals(value, result)
    }

    @Test
    fun getOrPut_does_not_overwrite_existing_value() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.getOrPut(key) { createValue(1) }

        assertEquals(value, cache.get(key))
    }

    @Test
    fun getOrPut_inserts_and_returns_default_when_key_is_absent() {
        val key = createKey(0)
        val value = createValue(0)

        val result = cache.getOrPut(key) { value }

        assertEquals(value, result)
        assertEquals(value, cache.get(key))
    }

    // replaceIfMatching

    @Test
    fun replaceIfMatching_replaces_value_when_old_value_matches() {
        val key = createKey(0)
        val oldValue = createValue(0)
        val newValue = createValue(1)

        cache.put(key, oldValue)

        val result = cache.replaceIfMatching(key, oldValue, newValue)

        assertTrue(result)
        assertEquals(newValue, cache.get(key))
    }

    @Test
    fun replaceIfMatching_does_not_replace_when_old_value_does_not_match() {
        val key = createKey(0)
        val currentValue = createValue(0)
        val wrongOldValue = createValue(1)
        val newValue = createValue(2)

        cache.put(key, currentValue)

        val result = cache.replaceIfMatching(key, wrongOldValue, newValue)

        assertFalse(result)
        assertEquals(currentValue, cache.get(key))
    }

    @Test
    fun replaceIfMatching_returns_false_when_key_is_absent() {
        val result = cache.replaceIfMatching(createKey(0), createValue(0), createValue(1))

        assertFalse(result)
    }

    // removeIfMatching

    @Test
    fun removeIfMatching_removes_entry_when_value_matches() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        val result = cache.removeIfMatching(key, value)

        assertTrue(result)
        assertNull(cache.get(key))
    }

    @Test
    fun removeIfMatching_does_not_remove_when_value_does_not_match() {
        val key = createKey(0)
        val value = createValue(0)
        val wrongValue = createValue(1)

        cache.put(key, value)

        val result = cache.removeIfMatching(key, wrongValue)

        assertFalse(result)
        assertEquals(value, cache.get(key))
    }

    @Test
    fun removeIfMatching_returns_false_when_key_is_absent() {
        val result = cache.removeIfMatching(createKey(0), createValue(0))

        assertFalse(result)
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
