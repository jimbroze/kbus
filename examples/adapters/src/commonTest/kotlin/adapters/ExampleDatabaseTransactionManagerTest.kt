package com.jimbroze.kbus.example.adapters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ExampleDatabaseTransactionManagerTest {
    @Test
    fun `keeps the writes its block made when the block returns`() = runTest {
        val database = ExampleDatabase()

        val result =
            ExampleDatabaseTransactionManager(database).execute {
                database.write("order-1", "placed")
                "done"
            }

        assertEquals("done", result)
        assertEquals("placed", database.read("order-1"))
    }

    @Test
    fun `undoes the writes its block made when the block throws`() = runTest {
        val database = ExampleDatabase()

        assertFailsWith<IllegalStateException> {
            ExampleDatabaseTransactionManager(database).execute {
                database.write("order-1", "placed")
                error("the handler blew up")
            }
        }

        assertNull(database.read("order-1"))
    }

    @Test
    fun `restores a row to the value it held before the failed block`() = runTest {
        val database = ExampleDatabase()
        database.write("order-1", "placed")

        assertFailsWith<IllegalStateException> {
            ExampleDatabaseTransactionManager(database).execute {
                database.write("order-1", "shipped")
                error("the handler blew up")
            }
        }

        assertEquals("placed", database.read("order-1"))
    }
}
