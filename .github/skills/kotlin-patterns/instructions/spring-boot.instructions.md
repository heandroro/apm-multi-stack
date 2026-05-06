---
description: "Use when building Spring Boot applications in Kotlin. Covers service layer patterns, repository design, REST controllers with validation, and coroutine integration."
---

# Spring Boot com Kotlin — Padrões

## Configuração do projeto

- Use Kotlin DSL no `build.gradle.kts`
- Habilite o plugin `kotlin-spring` (abre classes para proxy do Spring)
- Habilite `kotlin-jpa` se usar JPA (gera construtores no-arg)
- Configure `kotlin.compiler.incremental=true` para builds mais rápidos

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.jpa") version "2.0.0"
    id("org.springframework.boot") version "3.3.0"
}
```

## Controllers REST

- Use `@RestController` + `@RequestMapping` na classe
- Parâmetros de path e query com tipos não-nulos — o Spring valida automaticamente
- Use `ResponseEntity<T>` quando precisar controlar status code explicitamente
- Delegue toda lógica ao service — controllers são apenas roteadores

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: UUID): UserResponse =
        userService.findById(id)

    @PostMapping
    suspend fun create(
        @RequestBody @Valid request: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = userService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }
}
```

## Service Layer

- Anote com `@Service` e declare dependências como `val` no construtor
- Use `suspend fun` para operações assíncronas com Spring WebFlux ou coroutines
- Use `@Transactional` apenas no service, nunca no repository ou controller
- Lançe exceções de domínio específicas — não Spring exceptions genéricas

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    suspend fun create(request: CreateUserRequest): UserResponse {
        val user = User(name = request.name, email = request.email)
        val saved = userRepository.save(user)
        eventPublisher.publishEvent(UserCreatedEvent(saved.id))
        return saved.toResponse()
    }
}
```

## Repository com Spring Data

- Use `CoroutineCrudRepository` para operações assíncronas com coroutines
- Prefira query methods a `@Query` quando possível
- Use `Flow<T>` em vez de `List<T>` para streams de dados

```kotlin
interface UserRepository : CoroutineCrudRepository<User, UUID> {
    suspend fun findByEmail(email: String): User?
    fun findAllByActiveTrue(): Flow<User>
}
```

## Validação

- Use anotações Jakarta Validation (`@NotBlank`, `@Email`, `@Size`) nos DTOs
- Adicione `@Valid` nos parâmetros de controller para ativar validação
- Customize mensagens via `messages.properties`

## Tratamento de Erros Global

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException::class)
    fun handleNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "USER_NOT_FOUND", message = ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest()
            .body(ErrorResponse(code = "VALIDATION_ERROR", message = errors.joinToString(", ")))
    }
}
```
