package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.ThreadSafeMapCache
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
    CacheTestBase<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = ThreadSafeMapCache<String, String>()

    @Test
    fun concurrent_puts_to_500_different_keys_stores_all_values() = runTest {
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
    fun concurrent_puts_to_same_key_results_in_a_non_null_value() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i -> launch(Dispatchers.Default) { cache.put(key, createValue(i)) } }
        }

        val result = cache.get(key)
        assertTrue(result != null, "Value should not be null after concurrent puts")
    }

    @Test
    fun concurrent_reads_during_writes_do_not_corrupt_stored_values() = runTest {
        val cache = createCache()
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.put(createKey(i), createValue(i)) }
                launch(Dispatchers.Default) { cache.get(createKey(i)) }
            }
        }

        // All written values should be readable after concurrent operations complete
        repeat(count) { i -> assertEquals(createValue(i), cache.get(createKey(i))) }
    }

    @Test
    fun concurrent_removes_of_even_keys_preserves_all_odd_key_entries() = runTest {
        val cache = createCache()
        val count = 500

        repeat(count) { i -> cache.put(createKey(i), createValue(i)) }

        // Remove even-indexed keys concurrently
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
    fun concurrent_put_and_remove_on_same_key_does_not_crash_or_corrupt() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.put(key, createValue(i)) }
                launch(Dispatchers.Default) { cache.remove(key) }
            }
        }

        // After all operations, the key is either present or absent — no crash or corruption
        cache.get(key)
    }

    @Test
    fun concurrent_getOrPut_on_same_key_converges_to_single_value() = runTest {
        val cache = createCache()
        val key = createKey(0)
        val count = 500

        coroutineScope {
            repeat(count) { i ->
                launch(Dispatchers.Default) { cache.getOrPut(key) { createValue(i) } }
            }
        }

        // After all operations, the key holds exactly one of the candidate values
        val result = cache.get(key)
        val validValues = (0 until count).map { createValue(it) }.toSet()
        assertTrue(result != null && result in validValues)
    }

    @Test
    fun concurrent_getOrPut_on_different_keys_stores_all_values() = runTest {
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
    fun concurrent_replaceIfMatching_only_one_succeeds_per_value() = runTest {
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

        // Exactly one replace should have matched the original value
        assertEquals(1, successes.load())
        // The stored value should no longer be the original
        val finalValue = cache.get(key)
        assertTrue(finalValue != null && finalValue != original)
    }

    @Test
    fun concurrent_removeIfMatching_only_one_succeeds() = runTest {
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
    fun concurrent_replaceIfMatching_with_wrong_value_never_succeeds() = runTest {
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
    fun concurrent_removeIfMatching_with_wrong_value_never_succeeds() = runTest {
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
