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

        val existingWithDifferentHandler =
            existingWithSameMessage.firstOrNull { existing ->
                existing.handlerData.handlerClass != newItem.handlerData.handlerClass
            }
        return if (existingWithDifferentHandler !== null) {
            val messageClassName = newItem.handlerData.messageClass.simpleName
            val oldHandlerName = existingWithDifferentHandler.handlerData.handlerClass.simpleName
            val newHandlerName = newItem.handlerData.handlerClass.simpleName

            ConflictPolicy.Result.InvalidConflict(
                "Message class $messageClassName is used by multiple handlers: " +
                    "'$oldHandlerName' & '$newHandlerName'"
            )
        } else {
            ConflictPolicy.Result.ExactDuplicate
        }
    }
}
