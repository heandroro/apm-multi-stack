---
description: "Use when building HTTP servers or clients with Ktor in Kotlin. Covers routing DSL, plugins, authentication, serialization, and testing."
---

# Ktor — Padrões

## Configuração da Application

- Use `embeddedServer` com `Netty` como engine padrão
- Configure plugins em blocos `install { }` — não em funções soltas
- Separe routing em módulos (extension functions em `Application`)

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(RequestValidation)
    install(StatusPages) { configureStatusPages() }
    configureRouting()
    configureSecurity()
}
```

## Routing DSL

- Agrupe rotas por recurso em funções de extensão
- Use `call.receive<T>()` para deserializar o body (lança exceção se inválido)
- Use `call.respond()` com `HttpStatusCode` explícito
- Use `call.parameters["name"]` para path params, `call.request.queryParameters` para query

```kotlin
fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            userRoutes()
        }
    }
}

fun Route.userRoutes() {
    val service by inject<UserService>()

    get("/users/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val user = service.findById(id)
        call.respond(user)
    }

    post("/users") {
        val request = call.receive<CreateUserRequest>()
        val user = service.create(request)
        call.respond(HttpStatusCode.Created, user)
    }
}
```

## Serialização com kotlinx.serialization

```kotlin
@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    @Serializable(with = LocalDateSerializer::class)
    val createdAt: LocalDate
)
```

## Autenticação com JWT

```kotlin
fun Application.configureSecurity() {
    authentication {
        jwt("auth-jwt") {
            realm = "ktor-app"
            verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).withIssuer(jwtIssuer).build())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token inválido ou expirado")
            }
        }
    }
}

// Proteger rotas
authenticate("auth-jwt") {
    get("/me") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal!!.payload.getClaim("userId").asString()
        call.respond(service.findById(userId))
    }
}
```

## Status Pages (tratamento de erros)

```kotlin
fun StatusPagesConfig.configureStatusPages() {
    exception<NotFoundException> { call, cause ->
        call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
    }
    exception<RequestValidationException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons.joinToString(", ")))
    }
}
```

## Testes

- Use `testApplication { }` do `ktor-server-test-host`
- Configure módulos de teste com `application { module() }`
- Injete mocks de serviços via DI (Koin/Kodein)

```kotlin
@Test
fun `GET user returns 200`() = testApplication {
    application { module() }
    val response = client.get("/api/v1/users/123")
    assertEquals(HttpStatusCode.OK, response.status)
}
```
