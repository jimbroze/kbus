package com.jimbroze.kbus.infrastructure.cache.adapters

import com.jimbroze.kbus.infrastructure.cache.CacheContract

class MapCacheTest :
    CacheContract<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = MapCache<String, String>()
}
