import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ReturnCommand(val commandData: String) : Command()

class ReturnCommandHandler : CommandHandler<ReturnCommand> {
    override fun handle(message: ReturnCommand): Any {
        return message.commandData
    }
}

class PrintCommand(val commandData: String) : Command()

class PrintCommandHandler : CommandHandler<PrintCommand> {
    override fun handle(message: PrintCommand) {
        println(message.commandData)
    }
}

class AnyCommandHandler : CommandHandler<Command> {
    override fun handle(message: Command) {
    }
}

class TestMessageStore {
    @Test
    fun testExecuteAcceptsASpecificCommand() {
        val bus = MessageStore<Command>()

        bus.execute(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))
    }

    @Test
    fun test_execute_can_return_a_value() {
        val bus = MessageStore<Command>()

        val result = bus.execute(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))

        assertEquals("Testing", result)
    }

    @Test
    fun test_execute_cannot_execute_a_command_with_no_handlers() {
        val bus = MessageStore<Command>()

        assertThrows<MissingHandlerException> {
            bus.execute(ReturnCommand("Testing"))
        }
    }

    @Test
    fun test_execute_finds_a_previously_registered_command() {
        val bus = MessageStore<Command>()
        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        val result = bus.execute(ReturnCommand("Testing"))

        assertEquals("Testing", result)
    }

//    @Test
//    fun test_execute_does_not_accept_a_handler_if_one_is_already_registered() {
//        val bus = MessageStore<Command>()
//
//        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))
//
//        assertThrows<TooManyHandlersException> {
//            bus.execute(ReturnCommand("Testing"), listOf(AnyCommandHandler()))
//        }
//    }

    @Test
    fun test_is_registered_returns_false_for_non_registered_command() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(PrintCommand::class, listOf(PrintCommandHandler()))

        assert(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_is_registered_returns_true_for_registered_command() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        assert(bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_removeHandlers_removes_handler_for_a_given_command() {
        val bus = MessageStore<Command>()
        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        bus.removeHandlers(ReturnCommand::class)

        assert(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_removeHandlers_throws_exception_if_command_is_not_registered() {
        val bus = MessageStore<Command>()

        assertThrows<MissingHandlerException> {
            bus.removeHandlers(ReturnCommand::class)
        }
    }
}
