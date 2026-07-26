package com.jimbroze.kbus.contracts.inbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope

/**
 * User-supplied durable storage for a per-bounded-context inbox. Implementers commonly copy their
 * [com.jimbroze.kbus.contracts.outbox.OutboxStore] — two invariants below diverge materially from
 * it, so read carefully rather than assuming symmetry.
 *
 * All three methods are called outside any transaction — the opposite of `OutboxStore.save`, which
 * must join the ambient transaction. The counterpart obligation here: [save] must be durable on
 * return, because it is what [com.jimbroze.kbus.contracts.messages.event.EventDestination.deliver]
 * collapses to for an inboxed context, and its return is the ack that lets the producer's outbox
 * mark the entry published. A buffering [save] is a lost-event bug.
 *
 * The inbox dedupes *transport* redelivery, not *handler* re-execution: there is no transaction
 * between [fetchPending] and [markConsumed], so a crash mid-dispatch re-dispatches the same
 * envelope on restart. Handlers must still be idempotent.
 */
interface InboxStore {
    /**
     * Saves [envelopes], skipping any whose [EventEnvelope.id] is already known to this store —
     * that skip **is** the dedupe, so it must cover an id that is still pending, not only one
     * already consumed: a fetched-but-unacked envelope is otherwise re-savable, which
     * double-dispatches. Implement as a unique index plus an upsert-ignore, never read-then-write —
     * a pump and a bus instance can race a save for the same id.
     */
    suspend fun save(envelopes: List<EventEnvelope>)

    /**
     * Oldest-first, up to [limit] entries, excluding consumed ones. Implementations may hide
     * entries that are in-flight or too fresh, to shrink the window in which an opportunistic drain
     * and a scheduled pump tick could both fetch the same entry.
     */
    suspend fun fetchPending(limit: Int): List<EventEnvelope>

    /**
     * Marks the given ids consumed. Unlike `OutboxStore.markPublished`, this must **not** forget
     * the id: a consumed id is a dedupe tombstone, and [save] must go on rejecting it. Retain a
     * tombstone for at least the longest redelivery horizon of anything that can route here — in
     * practice, longer than the worst outage you intend to survive, since a producing outbox
     * retries an unacked entry indefinitely. A bounded retention window (days–weeks), pruned out of
     * band, is a reasonable default; pruning re-opens the duplicate window for anything redelivered
     * after the prune.
     */
    suspend fun markConsumed(ids: List<String>)
}
