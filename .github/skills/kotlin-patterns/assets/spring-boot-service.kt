package com.example.users

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// ─── Domain Model ─────────────────────────────────────────────────────────────

data class User(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val email: String,
    val active: Boolean = true
)

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class CreateUserRequest(
    @field:NotBlank val name: String,
    @field:Email @field:NotBlank val email: String
)

data class UserResponse(val id: UUID, val name: String, val email: String)

fun User.toResponse() = UserResponse(id = id, name = name, email = email)

// ─── Repository ───────────────────────────────────────────────────────────────

interface UserRepository : CoroutineCrudRepository<User, UUID> {
    suspend fun findByEmail(email: String): User?
    fun findAllByActiveTrue(): Flow<User>
}

// ─── Exceptions ───────────────────────────────────────────────────────────────

class UserNotFoundException(id: UUID) : RuntimeException("User '$id' not found")
class UserAlreadyExistsException(email: String) : RuntimeException("User with email '$email' already exists")

// ─── Service ──────────────────────────────────────────────────────────────────

@Service
class UserService(private val repository: UserRepository) {

    suspend fun findById(id: UUID): UserResponse =
        (repository.findById(id) ?: throw UserNotFoundException(id)).toResponse()

    fun findAll(): Flow<User> = repository.findAllByActiveTrue()

    suspend fun create(request: CreateUserRequest): UserResponse {
        if (repository.findByEmail(request.email) != null) {
            throw UserAlreadyExistsException(request.email)
        }
        val user = User(name = request.name, email = request.email)
        return repository.save(user).toResponse()
    }

    suspend fun delete(id: UUID) {
        if (!repository.existsById(id)) throw UserNotFoundException(id)
        repository.deleteById(id)
    }
}

// ─── Controller ───────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/v1/users")
@Validated
class UserController(private val service: UserService) {

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: UUID): UserResponse =
        service.findById(id)

    @PostMapping
    suspend fun create(
        @RequestBody @Valid request: CreateUserRequest
    ): ResponseEntity<UserResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(request))

    @DeleteMapping("/{id}")
    suspend fun delete(@PathVariable id: UUID): ResponseEntity<Unit> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}
