import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestMessageBus {

//    @Test
//    fun test_can_created_with_custom_command_and_event_bus() {
//        val commandBus = CommandBus()
//        val eventBus = EventBus()
//        val bus = MessageBus(commandBus, eventBus)
//
//        assertSame(bus.commandBus, commandBus)
//        assertSame(bus.eventBus, eventBus)
//    }

    @Test
    fun test_execute_executes_a_command() {
        val bus = MessageBus()

        bus.execute(PrintCommand("Test the bus"), PrintCommandHandler())
    }

    @Test
    fun test_execute_can_return_a_value() {
        val bus = MessageBus()

        val result = bus.execute(ReturnCommand("Test the bus"), ReturnCommandHandler())

        assertEquals(result, "Test the bus")
    }

    @Test
    fun test_command_registers_with_the_command_bus() {
        val bus = MessageBus()
        assertFalse {  bus.isRegistered(PrintCommand::class) }

        bus.register(PrintCommand::class, PrintCommandHandler())

        assertTrue {  bus.isRegistered(PrintCommand::class) }
    }

    @Test
    fun test_command_deregisters_with_the_command_bus() {
        val bus = MessageBus()

        bus.register(PrintCommand::class, PrintCommandHandler())
        bus.deregister(PrintCommand::class)

        assertFalse {  bus.isRegistered(PrintCommand::class) }
    }

    @Test
    fun test_is_registered_returns_false_for_command_not_registered() {
        val bus = MessageBus()

        assertFalse {  bus.isRegistered(ReturnCommand::class) }
    }

//    @Test
//    fun test_has_handlers_returns_zero_for_event_not_registered() {
//        val bus = MessageBus()
//
//        assert bus.has_handlers(ExampleEvent) == 0
//    }
}
