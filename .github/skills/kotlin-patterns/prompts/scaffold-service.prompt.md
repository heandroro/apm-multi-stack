---
name: scaffold-service
description: "Scaffold a new Kotlin service. Prompts for framework (Spring Boot or Ktor), resource name, and operations to generate."
---

Crie um serviço Kotlin completo com base nas seguintes escolhas:

**Framework**: $framework (Spring Boot | Ktor)
**Nome do recurso**: $resource (ex: User, Product, Order)
**Operações**: $operations (ex: CRUD completo | somente leitura | create e list)
**Persistência**: $persistence (ex: Spring Data JPA | Exposed | sem persistência)

## O que gerar

1. **Data classes / domain model** — entidade `$resource` com campos idiomáticos
2. **DTOs** — `Create${resource}Request`, `${resource}Response` (Pydantic-like validation ou data class)
3. **Repository** — interface com `CoroutineCrudRepository` (Spring) ou função de acesso a dados (Ktor)
4. **Service** — `${resource}Service` com `suspend fun` para cada operação
5. **Controller/Route** — endpoints REST com status codes corretos
6. **Exception classes** — `${resource}NotFoundException`, `${resource}AlreadyExistsException`
7. **Tratamento de erros** — `@RestControllerAdvice` (Spring) ou `StatusPages` (Ktor)

## Regras

- Use `suspend fun` em todas as funções do service e repository
- Use `UUID` como tipo de ID
- Retorne `ResponseEntity` com status code explícito (Spring) ou `HttpStatusCode` (Ktor)
- Inclua validação de input com Bean Validation (Spring) ou `RequestValidation` (Ktor)
- Siga as boas práticas em [kotlin.instructions.md](../../instructions/kotlin.instructions.md)
- Para Spring Boot: siga [spring-boot.instructions.md](../instructions/spring-boot.instructions.md)
- Para Ktor: siga [ktor.instructions.md](../instructions/ktor.instructions.md)

## Output esperado

Um arquivo `.kt` por componente, com imports corretos e sem dependências não declaradas.
