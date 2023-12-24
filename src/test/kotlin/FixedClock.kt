import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

public class FixedClock(private val fixedInstant: Instant): Clock {
    override fun now(): Instant = fixedInstant
}
