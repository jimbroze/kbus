package com.jimbroze.kbus.core.middleware.middleware.cache

import com.jimbroze.kbus.core.infrastructure.cache.Cache
import com.jimbroze.kbus.core.infrastructure.cache.MapCache
import com.jimbroze.kbus.core.infrastructure.cache.asTypedCache
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheAdapterTest {

    private open class Animal(val name: String) {
        override fun equals(other: Any?) = other is Animal && name == other.name

        override fun hashCode() = name.hashCode()

        override fun toString() = "Animal($name)"
    }

    private class Dog(name: String) : Animal(name) {
        override fun equals(other: Any?) = other is Dog && name == other.name

        override fun hashCode() = name.hashCode() + 1

        override fun toString() = "Dog($name)"
    }

    private class Cat(name: String) : Animal(name) {
        override fun equals(other: Any?) = other is Cat && name == other.name

        override fun hashCode() = name.hashCode() + 2

        override fun toString() = "Cat($name)"
    }

    private lateinit var underlyingCache: MapCache<String, Animal>
    private lateinit var typedCache: Cache<String, Dog>

    @BeforeTest
    fun setUp() {
        underlyingCache = MapCache()
        typedCache = underlyingCache.asTypedCache<String, Animal, Dog>()
    }

    @Test
    fun get_with_compatible_type_returns_value() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.get("key")

        assertEquals(Dog("Rex"), result)
    }

    @Test
    fun get_with_incompatible_type_returns_null() {
        underlyingCache.put("key", Cat("Whiskers"))

        val result = typedCache.get("key")

        assertNull(result)
    }

    @Test
    fun get_with_absent_key_returns_null() {
        assertNull(typedCache.get("missing"))
    }

    @Test
    fun put_stores_value_in_underlying_cache() {
        typedCache.put("key", Dog("Rex"))

        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun getOrPut_with_compatible_existing_value_returns_it() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.getOrPut("key") { Dog("Other") }

        assertEquals(Dog("Rex"), result)
    }

    @Test
    fun getOrPut_with_null_computes_and_stores() {
        val result = typedCache.getOrPut("key") { Dog("Rex") }

        assertEquals(Dog("Rex"), result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun getOrPut_with_incompatible_existing_value_replaces_it() {
        underlyingCache.put("key", Cat("Whiskers"))

        val result = typedCache.getOrPut("key") { Dog("Rex") }

        assertEquals(Dog("Rex"), result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun replaceIfMatching_delegates_correctly() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.replaceIfMatching("key", Dog("Rex"), Dog("Buddy"))

        assertTrue(result)
        assertEquals(Dog("Buddy"), underlyingCache.get("key"))
    }

    @Test
    fun replaceIfMatching_returns_false_when_not_matching() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.replaceIfMatching("key", Dog("Wrong"), Dog("Buddy"))

        assertFalse(result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun remove_delegates_correctly() {
        underlyingCache.put("key", Dog("Rex"))

        typedCache.remove("key")

        assertNull(underlyingCache.get("key"))
    }

    @Test
    fun removeIfMatching_delegates_correctly() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.removeIfMatching("key", Dog("Rex"))

        assertTrue(result)
        assertNull(underlyingCache.get("key"))
    }

    @Test
    fun removeIfMatching_returns_false_when_not_matching() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.removeIfMatching("key", Dog("Wrong"))

        assertFalse(result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun getOrPut_concurrent_incompatible_replacement_throws_error() {
        // Simulate: underlying cache has incompatible value and replaceIfMatching fails
        // (meaning something else changed it concurrently)
        val stubbedCache =
            object : Cache<String, Animal> {
                private var value: Animal? = Cat("Whiskers")

                override fun get(key: String): Animal? = value

                override fun put(key: String, value: Animal) {
                    this.value = value
                }

                override fun getOrPut(key: String, defaultValue: () -> Animal): Animal {
                    return value ?: defaultValue().also { value = it }
                }

                override fun replaceIfMatching(
                    key: String,
                    oldValue: Animal,
                    newValue: Animal,
                ): Boolean {
                    // Simulate concurrent modification: always fail
                    return false
                }

                override fun remove(key: String) {
                    value = null
                }

                override fun removeIfMatching(key: String, value: Animal): Boolean = false
            }

        val typed = stubbedCache.asTypedCache<String, Animal, Dog>()

        assertFailsWith<IllegalStateException> { typed.getOrPut("key") { Dog("Rex") } }
    }
}
