package com.jimbroze.kbus.core.infrastructure.lock.locks

import com.jimbroze.kbus.core.infrastructure.cache.Cache
import com.jimbroze.kbus.core.infrastructure.lock.AtomicLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CacheLock(private val cache: Cache<in String, String>, private val clock: Clock) :
    AtomicLock {
    override suspend fun tryAcquireLock(
        key: String,
        token: String,
        ttl: Duration,
        metadata: String?,
    ): Boolean {
        val now = clock.now()
        val expiresAt = now + ttl
        val newValue = encodeValue(token, expiresAt.toEpochMilliseconds(), metadata)

        val storedValue = cache.getOrPut(key) { newValue }
        if (storedValue == newValue) {
            return true
        }

        val storedData = decodeValue(storedValue)

        return if (storedData?.hasExpired(now) == true) {
            cache.replaceIfMatching(key, storedValue, newValue)
        } else {
            false
        }
    }

    override suspend fun getLockMetadata(key: String): String? {
        val currentValue = cache.get(key) ?: return null
        val currentData = decodeValue(currentValue)

        return if (currentData?.hasExpired(clock.now()) == true) {
            cache.removeIfMatching(key, currentValue)
            null
        } else {
            currentData?.metadata
        }
    }

    override suspend fun releaseLock(key: String, lockToken: String): Boolean {
        val currentValue = cache.get(key) ?: return false
        val currentData = decodeValue(currentValue)

        return if (currentData?.token == lockToken) {
            cache.removeIfMatching(key, currentValue)
        } else {
            false
        }
    }

    override suspend fun isLocked(key: String): Boolean {
        val currentValue = cache.get(key) ?: return false
        val currentData = decodeValue(currentValue)

        return if (currentData?.hasExpired(clock.now()) == true) {
            !cache.removeIfMatching(key, currentValue)
        } else {
            true
        }
    }

    private fun encodeValue(token: String, expiresAt: Long, metadata: String?): String {
        return Json.encodeToString(LockData(expiresAt, token, metadata))
    }

    private fun decodeValue(value: String): LockData? {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        return try {
            Json.decodeFromString<LockData>(value)
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
private data class LockData(val expiresAt: Long, val token: String, val metadata: String? = null) {
    fun hasExpired(now: Instant): Boolean = expiresAt <= now.toEpochMilliseconds()
}
