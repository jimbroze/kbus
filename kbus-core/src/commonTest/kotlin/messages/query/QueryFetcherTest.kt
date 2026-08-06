package com.jimbroze.kbus.core.messages.query

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QueryFetcherTest {
    @Test
    fun `returns what the query's handler returned`() = runTest {
        val fetcher = QueryFetcher(emptyList(), emptyContextFactory(backgroundScope))

        val createHandler = { StorageQueryHandler() }
        val result = fetcher.fetch(StorageQuery(0, mutableListOf("Wassup")), createHandler)

        assertEquals(BusResult.Companion.success("Wassup"), result)
    }
}
