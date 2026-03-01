package com.jimbroze.kbus.core.middleware.middleware.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

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
}
