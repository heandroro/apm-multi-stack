---
name: scaffold-service
description: "Scaffold a new Java service with Spring Boot, Quarkus, or Micronaut. Generates domain model, DTOs, repository, service, and REST controller."
---

Crie um serviço Java completo com base nas seguintes escolhas:

**Framework**: $framework (Spring Boot | Quarkus | Micronaut)
**Nome do recurso**: $resource (ex: User, Product, Order)
**Operações**: $operations (ex: CRUD completo | somente leitura | create e list)
**Persistência**: $persistence (ex: Spring Data JPA | Panache | Micronaut Data JDBC | sem persistência)
**Java version**: $java_version (ex: 21 | 17)

## O que gerar

1. **Entity / Domain model** — classe `$resource` com anotações JPA/Panache corretas
2. **DTOs** — `record Create${resource}Request` com validações Jakarta, `${resource}Response` com factory method
3. **Repository** — interface ou Panache entity com queries idiomáticas do framework
4. **Service** — `${resource}Service` com lógica de negócio e tratamento de erro
5. **Controller / Resource** — endpoints REST com status codes corretos
6. **Exception classes** — `${resource}NotFoundException`, `${resource}AlreadyExistsException`
7. **Error handler** — `@RestControllerAdvice` / `@ServerExceptionMapper` / `ExceptionHandler`

## Regras

- Use `record` para todos os DTOs (disponível a partir do Java 16/17)
- Use `UUID` como tipo de ID
- Use `Optional<T>` corretamente — nunca retorne null de métodos públicos
- Declare todas as dependências via construtor (injeção por construtor)
- Siga as boas práticas gerais em [java.instructions.md](../../instructions/java.instructions.md)
- Carregue as instruções da versão Java alvo:
  - Java 11 → [java-11.instructions.md](../instructions/java-11.instructions.md)
  - Java 17 → [java-17.instructions.md](../instructions/java-17.instructions.md)
  - Java 21 → [java-21.instructions.md](../instructions/java-21.instructions.md)
  - Java 25 → [java-25.instructions.md](../instructions/java-25.instructions.md)
- Para Spring Boot: siga [spring-boot.instructions.md](../instructions/spring-boot.instructions.md)
- Para Quarkus: siga [quarkus.instructions.md](../instructions/quarkus.instructions.md)
- Para Micronaut: siga [micronaut.instructions.md](../instructions/micronaut.instructions.md)
- **Java 11**: substitua `record` por classe com campos `final` + construtor; `.collect(Collectors.toUnmodifiableList())` em vez de `.toList()`
- **Java 21+**: considere Virtual Threads para operações de I/O bloqueantes

## Output esperado

Um arquivo `.java` por componente, com imports corretos, sem dependências não declaradas.
