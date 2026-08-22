package com.jimbroze.kbus.example.adapters

import com.jimbroze.kbus.infrastructure.transaction.TransactionManager

/** A stand-in for a real database, holding rows in memory. */
class ExampleDatabase {
    private val rows = mutableMapOf<String, String>()

    fun read(key: String): String? = rows[key]

    fun write(key: String, value: String) {
        rows[key] = value
    }

    internal fun snapshot(): Map<String, String> = rows.toMap()

    internal fun restore(snapshot: Map<String, String>) {
        rows.clear()
        rows.putAll(snapshot)
    }
}

/**
 * Runs a command's work against [database] as one transaction: everything it wrote stays on a
 * normal return, and the database goes back to what it held before if the work throws.
 *
 * Rollback restores the whole database rather than only the failed work's own writes, so
 * transactions overlapping in time will undo each other. Real durable storage is what fixes that,
 * not a cleverer in-memory adapter.
 */
class ExampleDatabaseTransactionManager(private val database: ExampleDatabase) :
    TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult {
        val beforeTheWork = database.snapshot()

        @Suppress("TooGenericExceptionCaught")
        try {
            return block()
        } catch (failure: Throwable) {
            database.restore(beforeTheWork)
            throw failure
        }
    }
}
