// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleMessages01

dependencies {
    implementation("com.jimbroze:kbus-core:<version>")

    // For KSP code generation (optional)
    implementation("com.jimbroze:kbus-annotations:<version>")
    ksp("com.jimbroze:kbus-generation:<version>")
}

// A command that returns a String result
class CreateUser(val name: String, val email: String) :
    Command<BusResult<String, MessageFailure>>()

class CreateUserHandler :
    CommandHandler<CreateUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: CreateUser): BusResult<String, MessageFailure> {
        // Create the user...
        return BusResult.success("User ${message.name} created")
    }
}

// A query that returns a User result
class GetUser(val id: Int) :
    Query<BusResult<User, MessageFailure>>()

class GetUserHandler :
    QueryHandler<GetUser, BusResult<User, MessageFailure>>() {

    override suspend fun handle(message: GetUser): BusResult<User, MessageFailure> {
        val user = userRepository.findById(message.id)
            ?: return BusResult.failure(GenericMessageFailure(GenericFailure("User not found")))
        return BusResult.success(user)
    }
}
