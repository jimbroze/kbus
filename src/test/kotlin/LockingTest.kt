import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

private var outputStreamCaptor = ByteArrayOutputStream()

class PrintReturnCommand(val messageData: String) : Command()

class PrintReturnCommandHandler : CommandHandler<PrintReturnCommand> {
    override suspend fun handle(message: PrintReturnCommand): String {
        println(message.messageData)

        return message.messageData
    }
}

class LockingPrintReturnCommand(val messageData: String) : Command(), LockingCommand

class LockingPrintReturnCommandHandler(private val locker: BusLocker) : CommandHandler<LockingPrintReturnCommand> {
    override suspend fun handle(message: LockingPrintReturnCommand): Any? {

        println("Pre-nested call")

        val result = locker.handle(PrintReturnCommand(message.messageData)) { c: PrintReturnCommand ->
            PrintReturnCommandHandler().handle(c)
        }

        println("Post-nested call")

        return result
    }
}

class LockingSleepCommand(val waitSecs: Float, val messageData: String) : Command(), LockingCommand

class LockingSleepCommandHandler : CommandHandler<LockingSleepCommand> {
    override suspend fun handle(message: LockingSleepCommand): Any {
        delay((1000 * message.waitSecs).toLong())

        return (message.messageData)
    }
}

class SleepCommand(val waitSecs: Float) : Command()

class SleepCommandHandler : CommandHandler<SleepCommand> {
    override suspend fun handle(message: SleepCommand) {
        delay((1000 * message.waitSecs).toLong())
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LockingTest {
    private val locker = BusLocker(Clock.System)

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
    fun message_locker_postpones_nested_command_and_returns_unit_instantly() {
        runBlocking {
            val result = locker.handle(LockingPrintReturnCommand("Nested call")) {
                LockingPrintReturnCommandHandler(locker).handle(it)
            }

            assertEquals(Unit, result)
        }

        val output = outputStreamCaptor.toString().trim()

        assert (output.indexOf("Pre-nested call") < output.indexOf("Post-nested call"))
        assert (output.indexOf("Post-nested call") < output.indexOf("Nested call"))
    }

    @Test
    fun message_locker_waits_to_execute_command_in_a_different_coroutine() {
        runBlocking {
            launch {
                val result1 = locker.handle(LockingSleepCommand(0.2f, "After sleep")) {
                    LockingSleepCommandHandler().handle(it)
                }
                println(result1)
            }
            launch {
                println("Before unlock")

                val result2 = locker.handle(ReturnCommand("After unlock")) {
                    ReturnCommandHandler().handle(it)
                }
                println(result2)
            }

            delay(50)
            assert(locker.busLocked)
        }

        val output = outputStreamCaptor.toString().trim()

        assert (output.indexOf("Before unlock") < output.indexOf("After sleep"))
        assert (output.indexOf("After sleep") < output.indexOf("After unlock"))
    }

    @Test
    fun bus_locker_does_not_lock_bus_from_a_message_not_implementing_locking_interface() {
        val locker = BusLocker(Clock.System)

        runBlocking {
            locker.handle(SleepCommand(0.2f)) { SleepCommandHandler().handle(it) }
            assert(!locker.busLocked)
        }
    }
}
