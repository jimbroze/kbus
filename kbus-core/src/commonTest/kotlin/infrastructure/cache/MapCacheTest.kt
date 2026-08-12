package com.jimbroze.kbus.core.infrastructure.cache

class MapCacheTest :
    CacheContract<String, String>(createKey = { "key-$it" }, createValue = { "value-$it" }) {
    override fun createCache() = MapCache<String, String>()
}
