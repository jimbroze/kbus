package com.jimbroze.kbus.generation.processing.handlers

import com.jimbroze.kbus.generation.processing.ConflictPolicy

object HandlerConflictPolicy : ConflictPolicy<HandlerDefinition> {
    override fun evaluate(
        newItem: HandlerDefinition,
        existingItems: Collection<HandlerDefinition>,
    ): ConflictPolicy.Result {
        val existingWithSameMessage =
            existingItems.filter { it.handlerData.messageClass == newItem.handlerData.messageClass }

        if (existingWithSameMessage.isEmpty()) {
            return ConflictPolicy.Result.Accept
        }

        val isExactDuplicate =
            existingWithSameMessage.any { existing ->
                existing.handlerData.handlerClass == newItem.handlerData.handlerClass
            }

        return when {
            isExactDuplicate -> ConflictPolicy.Result.ExactDuplicate
            newItem is EventHandlerDefinition -> ConflictPolicy.Result.Accept
            else -> {
                val existingHandler = existingWithSameMessage.first()
                val messageClassName = newItem.handlerData.messageClass.simpleName
                val oldHandlerName = existingHandler.handlerData.handlerClass.simpleName
                val newHandlerName = newItem.handlerData.handlerClass.simpleName

                ConflictPolicy.Result.InvalidConflict(
                    "Message class $messageClassName is used by multiple handlers: " +
                        "'$oldHandlerName' & '$newHandlerName'"
                )
            }
        }
    }
}
