package com.jimbroze.kbus.infrastructure.cache.adapters

import com.jimbroze.kbus.infrastructure.cache.CacheContract
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalAtomicApi::class)
class ThreadSafeMapCacheTest :
    CacheContract<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = ThreadSafeMapCache<String, String>()

    @Test
    fun `stores every value when many keys are written concurrently`() = runTest {
        val cache = createCache()
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.put(createKey(i), createValue(i)) }
            }
        }

        repeat(count) { i -> assertEquals(createValue(i), cache.get(createKey(i))) }
    }

    @Test
    fun `holds one of the written values when a key is written concurrently`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i -> launch(Dispatchers.Default) { cache.put(key, createValue(i)) } }
        }

        val result = cache.get(key)
        assertTrue(result != null)
    }

    @Test
    fun `keeps every written value readable when reads and writes interleave`() = runTest {
        val cache = createCache()
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.put(createKey(i), createValue(i)) }
                launch(Dispatchers.Default) { cache.get(createKey(i)) }
            }
        }

        repeat(count) { i -> assertEquals(createValue(i), cache.get(createKey(i))) }
    }

    @Test
    fun `leaves untouched keys in place when others are removed concurrently`() = runTest {
        val cache = createCache()
        val count = 500

        repeat(count) { i -> cache.put(createKey(i), createValue(i)) }

        coroutineScope {
            repeat(count) { i ->
                if (i % 2 == 0) {
                    launch(Dispatchers.Default) { cache.remove(createKey(i)) }
                }
            }
        }

        repeat(count) { i ->
            if (i % 2 == 0) {
                assertNull(cache.get(createKey(i)))
            } else {
                assertEquals(createValue(i), cache.get(createKey(i)))
            }
        }
    }

    @Test
    fun `stays readable when one key is written and removed concurrently`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.put(key, createValue(i)) }
                launch(Dispatchers.Default) { cache.remove(key) }
            }
        }

        cache.get(key)
    }

    @Test
    fun `settles on one of the candidate values when a key is filled concurrently`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.getOrPut(key) { createValue(i) } }
            }
        }

        val result = cache.get(key)
        val validValues = (0 until count).map { createValue(it) }.toSet()
        assertTrue(result != null && result in validValues)
    }

    @Test
    fun `stores every value when different keys are filled concurrently`() = runTest {
        val cache = createCache()
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.getOrPut(createKey(i)) { createValue(i) } }
            }
        }

        repeat(count) { i -> assertEquals(createValue(i), cache.get(createKey(i))) }
    }

    @Test
    fun `lets exactly one conditional replacement of a value win`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val original = createValue(0)
        cache.put(key, original)
        val count = 500

        val successes = AtomicInt(0)

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) {
                    if (cache.replaceIfMatching(key, original, createValue(i + 1))) {
                        successes.addAndFetch(1)
                    }
                }
            }
        }

        assertEquals(1, successes.load())
        val finalValue = cache.get(key)
        assertTrue(finalValue != null && finalValue != original)
    }

    @Test
    fun `lets exactly one conditional removal of an entry win`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val value = createValue(0)
        cache.put(key, value)
        val count = 500

        val successes = AtomicInt(0)

        coroutineScope {
            repeat(count) {
                launch(Dispatchers.Default) {
                    if (cache.removeIfMatching(key, value)) {
                        successes.addAndFetch(1)
                    }
                }
            }
        }

        assertEquals(1, successes.load())
        assertNull(cache.get(key))
    }

    @Test
    fun `refuses every conditional replacement whose expected value does not match`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        cache.put(key, createValue(0))
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) {
                    assertFalse(cache.replaceIfMatching(key, createValue(99), createValue(i + 1)))
                }
            }
        }

        assertEquals(createValue(0), cache.get(key))
    }

    @Test
    fun `refuses every conditional removal whose expected value does not match`() = runTest {
        val cache = createCache()
        val key = createKey(0)
        cache.put(key, createValue(0))
        val count = 500

        coroutineScope {
            repeat(count) {
                launch(Dispatchers.Default) {
                    assertFalse(cache.removeIfMatching(key, createValue(99)))
                }
            }
        }

        assertEquals(createValue(0), cache.get(key))
    }
}
