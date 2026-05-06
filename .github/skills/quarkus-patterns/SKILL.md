---
name: quarkus-patterns
description: "Quarkus patterns, templates and best practices. Use when building or reviewing Quarkus applications with RESTEasy Reactive, Panache ORM, CDI, SmallRye extensions, GraalVM native image, or Quarkus testing patterns."
argument-hint: "Describe what you want to build or review (e.g. 'REST endpoint with Panache', 'native image config', 'SmallRye Fault Tolerance', 'QuarkusTest with Dev Services')"
---

# Quarkus Patterns

Skill para boas práticas, scaffold e revisão de aplicações Quarkus.

## Tópicos cobertos

| Tópico | Descrição |
|--------|-----------|
| Quarkus Core | RESTEasy Reactive, Panache ORM (Active Record + Repository), CDI, Mutiny |
| Native Image | GraalVM, `@RegisterForReflection`, recursos, build steps |
| SmallRye | Fault Tolerance (`@Retry`, `@CircuitBreaker`), Health, OpenAPI, Config |
| Testes | `@QuarkusTest`, `@QuarkusIntegrationTest`, Dev Services, `@InjectMock` |

## Quando usar

- Criar endpoints REST com RESTEasy Reactive
- Modelar entidades e repositórios com Panache
- Configurar build de native image com GraalVM
- Adicionar resiliência com SmallRye Fault Tolerance
- Escrever testes com `@QuarkusTest` e Dev Services
- Migrar um serviço de Spring Boot para Quarkus

## Procedimento

1. Identifique o tópico alvo
2. Carregue as instruções específicas:
   - Core (REST, Panache, CDI, Mutiny) → [quarkus-core.instructions.md](./instructions/quarkus-core.instructions.md)
   - Native image → [quarkus-native.instructions.md](./instructions/quarkus-native.instructions.md)
   - SmallRye extensions → [smallrye.instructions.md](./instructions/smallrye.instructions.md)
   - Testes → [quarkus-testing.instructions.md](./instructions/quarkus-testing.instructions.md)
3. Para scaffold de novo serviço → execute [scaffold-quarkus-service](./prompts/scaffold-quarkus-service.prompt.md)
4. Para migração de Spring Boot → execute [migrate-spring-to-quarkus](./prompts/migrate-spring-to-quarkus.prompt.md)
5. Para trabalho aprofundado → delegue ao agente [quarkus-specialist](./agents/quarkus-specialist.agent.md)

## Integração com GitHub MCP

```
"Busque o template em {owner}/{repo}/path/to/Resource.java usando o GitHub MCP"
```
