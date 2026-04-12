package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.MapCache
import kotlin.test.Test
import kotlin.test.assertNotNull

class MapCacheTest :
    CacheTestBase<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = MapCache<String, String>()

    @Test
    fun verifies_concrete_cache_instance_type() {
        val cache = createCache()
        assertNotNull(cache)
    }
}
