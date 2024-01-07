abstract class Message {
    abstract val messageType: String

    final override fun toString(): String {
        return this::class.simpleName ?: ""
    }
}

// interface Handler {
//    abstract fun handle(message: Any): Any?
// }

class MissingHandlerException(
    message: String = "The requested Handler could not be found",
) : Exception(message)

class InvalidHandlerException(
    message: String = "The message and handler do not match",
) : Exception(message)
