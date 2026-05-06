---
name: scaffold-spring-service
description: "Scaffold a new Spring Boot REST service with optional Spring Cloud AWS integration. Generates domain model, DTOs, repository, service, and REST controller."
---

Crie um serviço Spring Boot completo com base nas seguintes escolhas:

**Nome do recurso**: $resource (ex: User, Product, Order)
**Operações**: $operations (ex: CRUD completo | somente leitura | create e list)
**Persistência**: $persistence (ex: Spring Data JPA + PostgreSQL | DynamoDB | sem persistência)
**AWS**: $aws_services (ex: nenhum | S3 | SQS + SNS | S3 + SQS)
**Java version**: $java_version (ex: 21 | 17)

## O que gerar

1. **Entity / Domain model** — classe `$resource` com anotações JPA ou `@DynamoDbBean`
2. **DTOs** — `record Create${resource}Request` com validações Jakarta + `${resource}Response` com factory method
3. **Repository** — interface `JpaRepository` ou `DynamoDbTemplate` wrapper
4. **Service** — `${resource}Service` com lógica de negócio e tratamento de erro
5. **Controller** — `@RestController` com endpoints REST e status codes corretos
6. **Exception classes** — `${resource}NotFoundException`, `${resource}AlreadyExistsException`
7. **Error handler** — `@RestControllerAdvice` global
8. **AWS integrations** (se solicitado) — serviços configurados por injeção de construtor

## Regras

- Carregue [spring-boot.instructions.md](../instructions/spring-boot.instructions.md) antes de gerar
- Se `$aws_services` for solicitado, carregue [spring-cloud-aws.instructions.md](../instructions/spring-cloud-aws.instructions.md)
- Use `record` para todos os DTOs (Java 16+)
- Use `UUID` como tipo de ID
- Injeção de dependência **somente por construtor** — nunca `@Autowired` em campos
- `Optional<T>` somente em valores de retorno — nunca como parâmetro ou campo
- **Java 21+**: considere Virtual Threads para operações de I/O bloqueantes

## Output esperado

Um arquivo `.java` por componente, com package, imports corretos e sem dependências não declaradas.
