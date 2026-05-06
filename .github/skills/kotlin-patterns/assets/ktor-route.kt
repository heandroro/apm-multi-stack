package com.example.users

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class CreateUserRequest(val name: String, val email: String)

@Serializable
data class UserResponse(val id: String, val name: String, val email: String)

@Serializable
data class ErrorResponse(val code: String, val message: String)

// ─── Routing ──────────────────────────────────────────────────────────────────

fun Application.configureUserRoutes() {
    routing {
        route("/api/v1") {
            userRoutes()
            authenticate("auth-jwt") {
                protectedUserRoutes()
            }
        }
    }
}

// Rotas públicas
fun Route.userRoutes() {
    val service = application.attributes[userServiceKey]

    post("/users") {
        val request = call.receive<CreateUserRequest>()
        if (request.name.isBlank() || request.email.isBlank()) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("VALIDATION_ERROR", "name and email are required")
            )
        }
        val user = service.create(request)
        call.respond(HttpStatusCode.Created, user)
    }

    get("/users/{id}") {
        val id = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_PARAM", "id is required"))

        val user = service.findById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User '$id' not found"))

        call.respond(user)
    }
}

// Rotas protegidas por JWT
fun Route.protectedUserRoutes() {
    val service = application.attributes[userServiceKey]

    delete("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val requesterId = principal?.payload?.getClaim("userId")?.asString()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_PARAM", "id is required"))

        service.delete(id, requesterId)
        call.respond(HttpStatusCode.NoContent)
    }
}
