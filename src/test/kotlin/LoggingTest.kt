import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private var outputStreamCaptor = ByteArrayOutputStream()

class PrintLogger : Logger {
    override fun info(message: String) {
        println("info: $message")
    }

    override fun error(message: String) {
        println("error: $message")
    }
}

class OtherPrintLogger : Logger {
    override fun info(message: String) {
        println("other-in: $message")
    }

    override fun error(message: String) {
        println("other-err: $message")
    }
}

class LoggingPrintCommand(message: String): PrintCommand(message), LoggingCommand
class LoggingPrintEvent(message: String): PrintEvent(message), LoggingEvent
class LoggingExceptionCommand: Command(), LoggingCommand

class ExceptionCommandHandler : CommandHandler<Command> {
    override fun handle(message: Command) {
        throw Exception("Exception raised")
    }
}
class LoggingExceptionEvent: Event(), LoggingEvent

class ExceptionEventHandler : EventHandler<Event> {
    override fun handle(message: Event) {
        throw Exception("Exception raised")
    }
}

class LoggingTest {

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
    fun message_logger_does_not_log_messages_that_do_not_implement_logging_interface() {
        val logger = MessageLogger(PrintLogger())

        logger.handle(PrintCommand("Testing")) { PrintCommandHandler().handle(it) }

        assertEquals("Testing", outputStreamCaptor.toString().trim())
    }

    @Test
    fun message_logger_logs_before_and_after_message_using_info() {
        val logger = MessageLogger(PrintLogger())

        logger.handle(LoggingPrintCommand("Testing")) { PrintCommandHandler().handle(it) }

        val output = outputStreamCaptor.toString().trim()
        val outputList = output.split("\n")

        assertEquals(3, outputList.size)
        assertContains(outputList[0], "info: ")
        assertEquals("Testing", outputList[1])
        assertContains(outputList[2], "info: Successfully")
    }

    @Test
    fun test_commands_log_with_correct_verbs() {
        val logger = MessageLogger(PrintLogger())

        logger.handle(LoggingPrintCommand("Testing")) { PrintCommandHandler().handle(it) }

        assertContains(outputStreamCaptor.toString().trim(), "Executing command")
        assertContains(outputStreamCaptor.toString().trim(), "executed command")
    }

    @Test
    fun test_commands_log_exception_and_rethrow() {
        val logger = MessageLogger(PrintLogger())

        assertThrows<Exception> {
            logger.handle(LoggingExceptionCommand()) { ExceptionCommandHandler().handle(it) }
        }

        assertContains(outputStreamCaptor.toString().trim(), "error: Failed executing")
    }

    @Test
    fun test_events_log_with_correct_verbs() {
        val logger = MessageLogger(PrintLogger())

        logger.handle(LoggingPrintEvent("Testing")) { PrintEventHandler().handle(it) }

        assertContains(outputStreamCaptor.toString().trim(), "Dispatching event")
        assertContains(outputStreamCaptor.toString().trim(), "dispatched event")
    }

    @Test
    fun test_events_log_exception_and_rethrow() {
        val logger = MessageLogger(PrintLogger())

        assertThrows<Exception> {
            logger.handle(LoggingExceptionEvent()) { ExceptionEventHandler().handle(it) }
        }

        assertContains(outputStreamCaptor.toString().trim(), "error: Failed dispatching")
    }
}
