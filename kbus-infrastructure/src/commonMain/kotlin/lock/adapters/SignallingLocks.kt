@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.infrastructure.lock.adapters

import com.jimbroze.kbus.infrastructure.cache.Cache
import com.jimbroze.kbus.infrastructure.lock.AtomicLock
import com.jimbroze.kbus.infrastructure.lock.PollingConfig
import com.jimbroze.kbus.infrastructure.lock.SignallingLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope

/**
 * Creates an in-memory [SignallingLock] backed by atomic operations. Suitable for single-machine
 * applications. Fully thread-safe.
 *
 * @param backgroundScope A [CoroutineScope] used for background lock-expiry coroutines.
 */
fun inMemoryAtomicLock(backgroundScope: CoroutineScope): SignallingLock =
    InMemoryAtomicSignallingLock(backgroundScope = backgroundScope)

/**
 * Creates a distributed [SignallingLock] that uses a [Cache] for atomic lock state and polls to
 * detect when locks are released. This is a convenience overload that wraps the cache in a
 * [CacheLock].
 *
 * **Note:** Polling introduces latency between a lock being released and waiters being notified. If
 * your infrastructure supports pub/sub (e.g. Redis Pub/Sub, database LISTEN/NOTIFY), you should
 * implement [SignallingLock] directly to get instant unlock notifications instead of relying on
 * polling.
 *
 * @param cache The distributed cache used for lock state storage.
 * @param clock Clock used to determine lock expiry.
 * @param backgroundScope A [CoroutineScope] used for background polling coroutines.
 * @param config Configuration for polling intervals, backoff, and timeout.
 */
fun pollingDistributedAtomicLock(
    cache: Cache<String, String>,
    clock: Clock,
    backgroundScope: CoroutineScope,
    config: PollingConfig = PollingConfig(),
): SignallingLock = pollingDistributedAtomicLock(CacheLock(cache, clock), backgroundScope, config)

/**
 * Creates a distributed [SignallingLock] that wraps an existing [AtomicLock] and polls to detect
 * when locks are released.
 *
 * **Note:** Polling introduces latency between a lock being released and waiters being notified. If
 * your infrastructure supports pub/sub (e.g. Redis Pub/Sub, database LISTEN/NOTIFY), you should
 * implement [SignallingLock] directly to get instant unlock notifications instead of relying on
 * polling.
 *
 * @param delegate The underlying [AtomicLock] that provides atomic lock operations.
 * @param backgroundScope A [CoroutineScope] used for background polling coroutines.
 * @param config Configuration for polling intervals, backoff, and timeout.
 */
fun pollingDistributedAtomicLock(
    delegate: AtomicLock,
    backgroundScope: CoroutineScope,
    config: PollingConfig = PollingConfig(),
): SignallingLock =
    PollingSignallingLock(delegate = delegate, backgroundScope = backgroundScope, config = config)
