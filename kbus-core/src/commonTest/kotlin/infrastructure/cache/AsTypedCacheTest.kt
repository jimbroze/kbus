package com.jimbroze.kbus.core.infrastructure.cache

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsTypedCacheTest {

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
    fun `returns a stored value of the expected type`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.get("key")

        assertEquals(Dog("Rex"), result)
    }

    @Test
    fun `returns null when the underlying cache holds another type`() {
        underlyingCache.put("key", Cat("Whiskers"))

        val result = typedCache.get("key")

        assertNull(result)
    }

    @Test
    fun `returns null for a key the underlying cache does not hold`() {
        assertNull(typedCache.get("missing"))
    }

    @Test
    fun `stores values in the underlying cache`() {
        typedCache.put("key", Dog("Rex"))

        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun `returns the existing value when it is of the expected type`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.getOrPut("key") { Dog("Other") }

        assertEquals(Dog("Rex"), result)
    }

    @Test
    fun `stores and returns the default when the underlying cache holds nothing`() {
        val result = typedCache.getOrPut("key") { Dog("Rex") }

        assertEquals(Dog("Rex"), result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun `replaces a value of another type when getting or putting`() {
        underlyingCache.put("key", Cat("Whiskers"))

        val result = typedCache.getOrPut("key") { Dog("Rex") }

        assertEquals(Dog("Rex"), result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun `replaces a matching value in the underlying cache`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.replaceIfMatching("key", Dog("Rex"), Dog("Buddy"))

        assertTrue(result)
        assertEquals(Dog("Buddy"), underlyingCache.get("key"))
    }

    @Test
    fun `refuses a conditional replacement when the value does not match`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.replaceIfMatching("key", Dog("Wrong"), Dog("Buddy"))

        assertFalse(result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun `removes the entry from the underlying cache`() {
        underlyingCache.put("key", Dog("Rex"))

        typedCache.remove("key")

        assertNull(underlyingCache.get("key"))
    }

    @Test
    fun `removes a matching entry from the underlying cache`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.removeIfMatching("key", Dog("Rex"))

        assertTrue(result)
        assertNull(underlyingCache.get("key"))
    }

    @Test
    fun `refuses a conditional removal when the value does not match`() {
        underlyingCache.put("key", Dog("Rex"))

        val result = typedCache.removeIfMatching("key", Dog("Wrong"))

        assertFalse(result)
        assertEquals(Dog("Rex"), underlyingCache.get("key"))
    }

    @Test
    fun `fails rather than guess when another writer displaces the value it is replacing`() {
        val cacheWhoseReplacementsAlwaysLose =
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
                ): Boolean = false

                override fun remove(key: String) {
                    value = null
                }

                override fun removeIfMatching(key: String, value: Animal): Boolean = false
            }

        val typed = cacheWhoseReplacementsAlwaysLose.asTypedCache<String, Animal, Dog>()

        assertFailsWith<IllegalStateException> { typed.getOrPut("key") { Dog("Rex") } }
    }
}
