import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

private var outputStreamCaptor = ByteArrayOutputStream()

class MiddlewareTest {
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
    fun test_MessageLogger_logs_and_executes_command() {
        val bus = MessageBus(listOf(MessageLogger(PrintLogger())))

        runBlocking {
            bus.execute(LoggingPrintCommand("Test the bus"), PrintCommandHandler())
        }

        val output = outputStreamCaptor.toString().trim()
        val outputList = output.split("\n")

        assertEquals(3, outputList.size)
        assertContains(output, "Test the bus")
    }

    @Test
    fun test_MessageBus_handlers_middleware_in_the_correct_order() {
        val bus = MessageBus(listOf(
            MessageLogger(PrintLogger()),
            MessageLogger(PrintLogger()),
            MessageLogger(OtherPrintLogger()),
        ))

        runBlocking {
            bus.execute(LoggingPrintCommand("Test the bus"), PrintCommandHandler())
        }

        val output = outputStreamCaptor.toString().trim()
        val outputList = output.split("\n")

        assertEquals(7, outputList.size)
        assertContains(outputList[0], "info")
        assertContains(outputList[1], "info")
        assertContains(outputList[2], "other-in")
        assertContains(outputList[3], "Test the bus")
        assertContains(outputList[4], "other-in")
        assertContains(outputList[5], "info")
        assertContains(outputList[6], "info")
    }
}
