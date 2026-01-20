package com.jimbroze.kbus.core.messages.query

import com.jimbroze.kbus.core.bus.StorageQuery
import com.jimbroze.kbus.core.bus.StorageQueryHandler
import com.jimbroze.kbus.core.result.BusResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QueryFetcherTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val fetcher = QueryFetcher(emptyList())

        val createHandler = { StorageQueryHandler() }
        val result = fetcher.fetch(StorageQuery(0, mutableListOf("Wassup")), createHandler)

        assertEquals(BusResult.Companion.success("Wassup"), result)
    }
}
