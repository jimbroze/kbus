package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QueryFetcherTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val fetcher = QueryFetcher(emptyList())

        val result = fetcher.fetch(StorageQuery(0, mutableListOf("Wassup")), StorageQueryHandler())

        assertEquals(BusResult.success("Wassup"), result)
    }
}
