---
description: "Use when building Spring Boot 3.x applications in Java. Covers REST controllers, service layer, Spring Data JPA, validation, security, and observability patterns."
---

# Spring Boot 3.x com Java — Padrões

## Projeto base

```xml
<!-- pom.xml — dependências essenciais -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
</parent>

<dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
</dependencies>
```

## Controllers REST

- Use `@RestController` + `@RequestMapping` na classe
- Use `record` para request/response DTOs
- Retorne `ResponseEntity<T>` quando precisar controlar o status code
- Use `@Valid` para ativar validação de Bean Validation

```java
@RestController
@RequestMapping("/api/v1/users")
class UserController {
    private final UserService service;

    UserController(UserService service) { this.service = service; }

    @GetMapping("/{id}")
    ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@RequestBody @Valid CreateUserRequest request) {
        var user = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## DTOs com Records

```java
public record CreateUserRequest(
    @NotBlank String name,
    @Email @NotBlank String email
) {}

public record UserResponse(UUID id, String name, String email) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
```

## Service Layer

```java
@Service
@Transactional(readOnly = true)
class UserService {
    private final UserRepository repository;

    UserService(UserRepository repository) { this.repository = repository; }

    public UserResponse findById(UUID id) {
        return repository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }
        var user = new User(request.name(), request.email());
        return UserResponse.from(repository.save(user));
    }
}
```

## Spring Data JPA

```java
interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.active = true ORDER BY u.name")
    List<User> findAllActive();
}
```

## Tratamento Global de Erros

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", errors));
    }
}

public record ErrorResponse(String code, String message) {}
```

## Observability

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
```
