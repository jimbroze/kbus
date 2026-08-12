package com.jimbroze.kbus.core.infrastructure.cache

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The requirements every [Cache] implementation must meet, whatever its key and value types. */
abstract class CacheContract<K : Any, V : Any>(
    protected val createKey: (Int) -> K,
    protected val createValue: (Int) -> V,
) {
    protected lateinit var cache: Cache<K, V>

    /** Must return a fresh, empty cache; it is called once per test. */
    abstract fun createCache(): Cache<K, V>

    @BeforeTest
    fun setUp() {
        cache = createCache()
    }

    @Test
    fun `returns null for a key it has never held`() {
        assertNull(cache.get(createKey(0)))
    }

    @Test
    fun `returns the value stored under a key`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        assertEquals(value, cache.get(key))
    }

    @Test
    fun `replaces the previous value when a key is stored twice`() {
        val key = createKey(0)
        val firstValue = createValue(0)
        val secondValue = createValue(1)

        cache.put(key, firstValue)
        cache.put(key, secondValue)

        assertEquals(secondValue, cache.get(key))
    }

    @Test
    fun `returns null for a key that has been removed`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.remove(key)

        assertNull(cache.get(key))
    }

    @Test
    fun `ignores a removal for a key it does not hold`() {
        cache.remove(createKey(0))
    }

    @Test
    fun `stores values under different keys independently`() {
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
    fun `leaves other keys in place when one is removed`() {
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
    fun `stores a new value when a removed key is used again`() {
        val key = createKey(0)
        val firstValue = createValue(0)
        val secondValue = createValue(1)

        cache.put(key, firstValue)
        cache.remove(key)
        cache.put(key, secondValue)

        assertEquals(secondValue, cache.get(key))
    }

    @Test
    fun `leaves the entry in place when it is read`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.get(key)

        assertEquals(value, cache.get(key))
    }

    // getOrPut

    @Test
    fun `returns the existing value when getting or putting a key it holds`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        val result = cache.getOrPut(key) { createValue(1) }

        assertEquals(value, result)
    }

    @Test
    fun `leaves the existing value in place when getting or putting a key it holds`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)
        cache.getOrPut(key) { createValue(1) }

        assertEquals(value, cache.get(key))
    }

    @Test
    fun `stores and returns the default when getting or putting an absent key`() {
        val key = createKey(0)
        val value = createValue(0)

        val result = cache.getOrPut(key) { value }

        assertEquals(value, result)
        assertEquals(value, cache.get(key))
    }

    // replaceIfMatching

    @Test
    fun `replaces the value when the expected old value matches`() {
        val key = createKey(0)
        val oldValue = createValue(0)
        val newValue = createValue(1)

        cache.put(key, oldValue)

        val result = cache.replaceIfMatching(key, oldValue, newValue)

        assertTrue(result)
        assertEquals(newValue, cache.get(key))
    }

    @Test
    fun `leaves the value in place when the expected old value does not match`() {
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
    fun `refuses a conditional replacement for an absent key`() {
        val result = cache.replaceIfMatching(createKey(0), createValue(0), createValue(1))

        assertFalse(result)
    }

    // removeIfMatching

    @Test
    fun `removes the entry when the expected value matches`() {
        val key = createKey(0)
        val value = createValue(0)

        cache.put(key, value)

        val result = cache.removeIfMatching(key, value)

        assertTrue(result)
        assertNull(cache.get(key))
    }

    @Test
    fun `leaves the entry in place when the expected value does not match`() {
        val key = createKey(0)
        val value = createValue(0)
        val wrongValue = createValue(1)

        cache.put(key, value)

        val result = cache.removeIfMatching(key, wrongValue)

        assertFalse(result)
        assertEquals(value, cache.get(key))
    }

    @Test
    fun `refuses a conditional removal for an absent key`() {
        val result = cache.removeIfMatching(createKey(0), createValue(0))

        assertFalse(result)
    }

    @Test
    fun `stores every entry when many keys are in use`() {
        val count = 100
        val keys = (0 until count).map { createKey(it) }
        val values = (0 until count).map { createValue(it) }

        keys.zip(values).forEach { (k, v) -> cache.put(k, v) }

        keys.zip(values).forEach { (k, v) -> assertEquals(v, cache.get(k)) }
    }
}
