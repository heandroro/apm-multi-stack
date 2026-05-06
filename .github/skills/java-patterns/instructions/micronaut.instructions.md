---
description: "Use when building Micronaut applications in Java. Covers HTTP controllers, dependency injection, data access with Micronaut Data, HTTP client, and GraalVM native image patterns."
---

# Micronaut — Padrões

## Projeto base

```bash
mn create-app com.example.my-service \
  --features=http-server,data-jdbc,postgres,jackson,graalvm
```

## HTTP Controllers

- Use `@Controller` com path base na classe
- Parâmetros anotados: `@PathVariable`, `@QueryValue`, `@Body`
- Retorne `HttpResponse<T>` para controle explícito de status
- Use `@Validated` + anotações Jakarta para validação

```java
@Controller("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @Get("/{id}")
    public HttpResponse<UserResponse> findById(UUID id) {
        return HttpResponse.ok(service.findById(id));
    }

    @Post
    @Status(HttpStatus.CREATED)
    public UserResponse create(@Body @Valid CreateUserRequest request) {
        return service.create(request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(UUID id) {
        service.delete(id);
    }
}
```

## Injeção de Dependência (IoC)

- Use `@Singleton` para beans de vida útil da aplicação
- Prefira injeção via construtor — Micronaut gera o código no compile-time (sem proxy)
- Use `@Requires` para beans condicionais (feature flags, environment)

```java
@Singleton
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) { this.repository = repository; }
}

// Bean condicional
@Singleton
@Requires(env = "production")
public class ProductionEmailService implements EmailService { ... }
```

## Micronaut Data JDBC

```java
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface UserRepository extends CrudRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT * FROM users WHERE active = TRUE ORDER BY name")
    List<User> findAllActive();
}
```

## HTTP Client declarativo

```java
@Client("/api/external")
public interface ExternalApiClient {

    @Get("/users/{id}")
    Optional<ExternalUserDto> findUser(@PathVariable String id);

    @Post("/events")
    HttpResponse<Void> publishEvent(@Body EventRequest event);
}
```

## Tratamento de Erros

```java
@Produces
@Singleton
public class GlobalErrorHandler implements ExceptionHandler<UserNotFoundException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, UserNotFoundException ex) {
        return HttpResponse.notFound(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }
}
```

## GraalVM Native Image

```bash
./gradlew nativeCompile
# ou com Docker
./gradlew dockerBuildNative
```

Micronaut compila tudo no build-time (AOT), o que torna a compatibilidade com native image mais simples do que Spring ou Quarkus. Evite apenas:
- Reflection dinâmica não declarada
- Geração de bytecode em runtime (ex: Groovy)

## Testes

```java
@MicronautTest
class UserControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testCreateUser() {
        var request = HttpRequest.POST("/api/v1/users",
            new CreateUserRequest("Bob", "bob@example.com"));
        var response = client.toBlocking().exchange(request, UserResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }
}
```
