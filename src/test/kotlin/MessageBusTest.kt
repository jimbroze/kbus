import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private var outputStreamCaptor = ByteArrayOutputStream()

class TestMessageBus {

    @BeforeEach
    fun setUp() {
        outputStreamCaptor = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStreamCaptor))
    }

    @AfterEach
    fun tearDown() {
        System.setOut(System.out)
    }

    @Test
    fun test_execute_executes_a_command() {
        val bus = MessageBus()

        runBlocking {
            bus.execute(PrintCommand("Test the bus"), PrintCommandHandler())
        }

        assertEquals("Test the bus", outputStreamCaptor.toString().trim())
    }

    @Test
    fun test_execute_can_return_a_value() {
        val bus = MessageBus()

        runBlocking {
            val result = bus.execute(ReturnCommand("Test the bus"), ReturnCommandHandler())

            assertEquals(result, "Test the bus")
        }

    }

    @Test
    fun test_execute_does_not_accept_a_handler_if_one_is_already_registered() {
        val bus = MessageBus()

        bus.register(ReturnCommand::class, ReturnCommandHandler())

        assertThrows<TooManyHandlersException> {
            runBlocking {
                bus.execute(ReturnCommand("Testing"), AnyCommandHandler())
            }
        }
    }

    @Test
    fun test_dispatch_dispatches_an_event() {
        val bus = MessageBus()

        runBlocking {
            bus.dispatch(PrintEvent("Test the bus"), listOf(PrintEventHandler()))
        }

        assertEquals("Test the bus", outputStreamCaptor.toString().trim())
    }
    @Test
    fun test_dispatch_can_dispatch_an_event_with_no_handlers() {
        val bus = MessageBus()

        runBlocking {
            bus.dispatch(PrintEvent("Test the bus"))
        }
    }

    @Test
    fun test_dispatch_can_dispatch_multiple_events() {
        val bus = MessageBus()

        runBlocking {
            bus.dispatch(PrintEvent("Test the bus"), listOf(PrintEventHandler(), OtherPrintEventHandler()))
        }

        assertEquals("Test the bus\nTest the bus", outputStreamCaptor.toString().trim())
    }

    @Test
    fun test_command_registers_with_the_command_bus() {
        val bus = MessageBus()
        assertFalse {  bus.isRegistered(PrintCommand::class) }

        bus.register(PrintCommand::class, PrintCommandHandler())

        assertTrue {  bus.isRegistered(PrintCommand::class) }
    }

    @Test
    fun test_execute_executes_a_previously_registered_command() {
        val bus = MessageBus()
        bus.register(ReturnCommand::class, ReturnCommandHandler())

        runBlocking {
            val result = bus.execute(ReturnCommand("Test the bus"))

            assertEquals(result, "Test the bus")
        }

    }

    @Test
    fun test_events_can_register_multiple_handlers() {
        val bus = MessageBus()
        assertEquals(0, bus.hasHandlers(PrintEvent::class))

        bus.register(PrintEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))

        assertEquals(2, bus.hasHandlers(PrintEvent::class))
    }

    @Test
    fun test_dispatch_dispatches_previously_registered_event() {
        val bus = MessageBus()
        bus.register(PrintEvent::class, listOf(PrintEventHandler()))

        runBlocking {
            bus.dispatch(PrintEvent("Test the bus"))
        }

        assertEquals("Test the bus", outputStreamCaptor.toString().trim())
    }

    @Test
    fun test_dispatch_combines_registered_and_passed_events() {
        val bus = MessageBus()
        bus.register(PrintEvent::class, listOf(PrintEventHandler()))

        runBlocking {
            bus.dispatch(PrintEvent("Test the bus"), listOf(OtherPrintEventHandler()))
        }

        assertEquals("Test the bus\nTest the bus", outputStreamCaptor.toString().trim())
    }

    @Test
    fun test_command_deregisters_with_the_command_bus() {
        val bus = MessageBus()

        bus.register(PrintCommand::class, PrintCommandHandler())
        bus.deregister(PrintCommand::class)

        assertFalse { bus.isRegistered(PrintCommand::class) }
    }

    @Test
    fun test_bus_can_deregister_multiple_events_at_once() {
        val bus = MessageBus()
        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler()
        val handler3 = PrintEventHandler()
        bus.register(PrintEvent::class, listOf(handler1, handler2, handler3))
        assertEquals(3, bus.hasHandlers(PrintEvent::class))

        bus.deregister(PrintEvent::class, listOf(handler2, handler3))

        assertEquals(1, bus.hasHandlers(PrintEvent::class))
    }

    @Test
    fun test_bus_deregisters_all_events_by_default() {
        val bus = MessageBus()
        bus.register(PrintEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))
        assertEquals(2, bus.hasHandlers(PrintEvent::class))

        bus.deregister(PrintEvent::class)

        assertEquals(0, bus.hasHandlers(PrintEvent::class))
    }

    @Test
    fun test_is_registered_returns_false_for_command_not_registered() {
        val bus = MessageBus()

        assertFalse {  bus.isRegistered(ReturnCommand::class) }
    }

    @Test
    fun test_has_handlers_returns_zero_for_event_not_registered() {
        val bus = MessageBus()

        assertEquals(0, bus.hasHandlers(PrintEvent::class))
    }
}
