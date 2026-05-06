---
name: scaffold-quarkus-service
description: "Scaffold a new Quarkus REST service with Panache ORM, CDI, optional SmallRye Fault Tolerance, and QuarkusTest. Generates resource, service, repository/entity, DTOs, and error handling."
---

Crie um serviço Quarkus completo com base nas seguintes escolhas:

**Nome do recurso**: $resource (ex: Order, Product, User)
**Operações**: $operations (ex: CRUD completo | somente leitura | create e list)
**Panache style**: $panache_style (Active Record | Repository)
**Persistência**: $persistence (ex: PostgreSQL | MySQL | sem persistência)
**Fault Tolerance**: $fault_tolerance (sim | não)
**Native image**: $native (sim | não)

## O que gerar

1. **Entity** — `$resource` com Panache (Active Record ou `PanacheRepositoryBase`), `@Enumerated(STRING)`, `@RegisterForReflection` se native
2. **DTOs** — `record Create${resource}Request` com validações Jakarta + `${resource}Response` com factory method
3. **Repository** (se style = Repository) — `${resource}Repository` com queries idiomáticas
4. **Service** — `${resource}Service` `@ApplicationScoped` com `@Transactional`, retornando `Uni<T>`
5. **Resource** — `${resource}Resource` com `@Path`, `@GET`, `@POST`, `@DELETE`, `@ServerExceptionMapper`
6. **Exception classes** — `${resource}NotFoundException`
7. **Fault Tolerance** (se solicitado) — `@Retry` + `@CircuitBreaker` + `@Fallback` em chamadas externas
8. **Testes** — `@QuarkusTest` com RestAssured + `@InjectMock` onde necessário

## Regras

- Carregue [quarkus-core.instructions.md](../instructions/quarkus-core.instructions.md) antes de gerar
- Se `$fault_tolerance = sim`, carregue [smallrye.instructions.md](../instructions/smallrye.instructions.md)
- Se `$native = sim`, carregue [quarkus-native.instructions.md](../instructions/quarkus-native.instructions.md) e adicione `@RegisterForReflection` nos DTOs
- Use `Uni<T>` em todos os métodos de serviço e resource — nunca bloqueie a event loop
- Use `@ApplicationScoped` em service e repository — nunca instancie beans manualmente
- Injeção de dependência por construtor quando possível

## Output esperado

Um arquivo `.java` por componente com package, imports corretos e `application.properties` mínimo.
