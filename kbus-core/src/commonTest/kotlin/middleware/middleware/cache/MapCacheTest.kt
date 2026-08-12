package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.MapCache

class MapCacheTest :
    CacheContract<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = MapCache<String, String>()
}
