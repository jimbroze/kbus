import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ReturnCommand(val commandData: String) : Command()

class ReturnCommandHandler : CommandHandler<ReturnCommand> {
    override fun handle(command: ReturnCommand): Any {
        return command.commandData
    }
}

class PrintCommand(val commandData: String) : Command()

class PrintCommandHandler : CommandHandler<PrintCommand> {
    override fun handle(command: PrintCommand) {
        println(command.commandData)
    }
}

class AnyCommandHandler : CommandHandler<Command> {
    override fun handle(command: Command) {
    }
}

class TestCommandBus {
    @Test
    fun testExecuteAcceptsASpecificCommand() {
        val bus = CommandBus()

        bus.execute(ReturnCommand("Testing"), ReturnCommandHandler())
    }

    @Test
    fun test_execute_can_return_a_value() {
        val bus = CommandBus()

        val result = bus.execute(ReturnCommand("Testing"), ReturnCommandHandler())

        assertEquals("Testing", result)
    }

    @Test
    fun `test execute cannot execute a command with no handlers`() {
        val bus = CommandBus()

        assertThrows<MissingHandlerException> {
            bus.execute(ReturnCommand("Testing"))
        }
    }

    @Test
    fun `test execute finds a previously registered command`() {
        val bus = CommandBus()
        bus.registerHandler(ReturnCommand::class, ReturnCommandHandler())

        val result = bus.execute(ReturnCommand("Testing"))

        assertEquals("Testing", result)
    }

    @Test
    fun test_execute_does_not_accept_a_handler_if_one_is_already_registered() {
        val bus = CommandBus()

        bus.registerHandler(ReturnCommand::class, ReturnCommandHandler())

        assertThrows<TooManyHandlersException> {
            bus.execute(ReturnCommand("Testing"), AnyCommandHandler())
        }
    }

    @Test
    fun test_is_registered_returns_false_for_non_registered_command() {
        val bus = CommandBus()

        bus.registerHandler(PrintCommand::class, PrintCommandHandler())

        assert(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_is_registered_returns_true_for_registered_command() {
        val bus = CommandBus()

        bus.registerHandler(ReturnCommand::class, ReturnCommandHandler())

        assert(bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_remove_handler_removes_handler_for_a_given_command() {
        val bus = CommandBus()
        bus.registerHandler(ReturnCommand::class, ReturnCommandHandler())

        bus.removeHandler(ReturnCommand::class)

        assert(!bus.isRegistered(ReturnCommand::class))
        assertThrows<MissingHandlerException> {
            bus.execute(ReturnCommand("Testing"))
        }
    }

    @Test
    fun test_remove_handler_throws_exception_if_command_is_not_registered() {
        val bus = CommandBus()

        assertThrows<MissingHandlerException> {
            bus.removeHandler(ReturnCommand::class)
        }
    }
}
