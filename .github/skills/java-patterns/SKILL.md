---
name: java-patterns
description: "Java design patterns, framework templates and best practices. Use when reviewing idiomatic Java code (11/17/21/25), scaffolding Micronaut services, or writing framework-agnostic JUnit 5 / Mockito / AssertJ tests. For Spring Boot slices (@WebMvcTest, @DataJpaTest) use spring-patterns. For Quarkus use quarkus-patterns."
argument-hint: "Describe what you want to build or review (e.g. 'Micronaut HTTP client', 'Java 21 virtual threads', 'JUnit 5 parameterized test', 'MapStruct DTO mapping')"
---

# Java Patterns

Skill para boas práticas, scaffold e revisão de código Java com frameworks modernos.

## Frameworks e tópicos cobertos

| Tópico | Descrição |
|--------|-----------|
| Java 11 / 17 / 21 / 25 | Idioms por versão LTS: `var`, records, sealed classes, Virtual Threads, String Templates |
| Quarkus | Cloud-native, GraalVM native image, Panache, CDI | → use a skill **[quarkus-patterns](../quarkus-patterns/SKILL.md)** |
| Micronaut | AOT compilation, GraalVM friendly, HTTP client/server |
| MapStruct | Mapeamento entity ↔ DTO gerado em compile-time, qualifiers, update parcial |
| HTTP Clients | JDK HttpClient, RestClient, WebClient, @HttpExchange, Apache HttpClient 5, OkHttp |
| JUnit 5 | Unit, slice e integration tests com Mockito, AssertJ, Testcontainers |
| Testcontainers | Core framework-agnostic: `@Container`, GenericContainer, wait strategies, redes, reuso. Spring Boot → `spring-patterns`. Quarkus → Dev Services automático |
| BDD | Cucumber 7 + JUnit 5, Gherkin, step definitions, Spring Boot integration, DataTable |
| Spring Boot / Spring Cloud AWS | → use a skill **[spring-patterns](../spring-patterns/SKILL.md)** |

## Quando usar

- Revisar código Java em busca de padrões não-idiomáticos ou Java legado
- Scaffoldar um serviço com Micronaut
- Escrever ou revisar testes Java (unitários, slice, integração)
- Trabalhar com idioms específicos de uma versão LTS (11, 17, 21, 25)
- Implementar design patterns com Java moderno (records, sealed classes, pattern matching)
- Quarkus → use `/quarkus-patterns`
- Spring Boot ou Spring Cloud AWS → use `/spring-patterns`

## Procedimento

1. Identifique o framework/tópico alvo
2. **Quarkus?** → use a skill **[quarkus-patterns](../quarkus-patterns/SKILL.md)** diretamente
3. **Spring Boot ou Spring Cloud AWS?** → use a skill **[spring-patterns](../spring-patterns/SKILL.md)** diretamente
4. Carregue as instruções específicas para os demais casos:
   - Micronaut → [micronaut.instructions.md](./instructions/micronaut.instructions.md)
   - MapStruct → [mapstruct.instructions.md](./instructions/mapstruct.instructions.md)
   - HTTP Clients → [http-clients.instructions.md](./instructions/http-clients.instructions.md)
   - Testes (JUnit 5) → [junit.instructions.md](./instructions/junit.instructions.md)
   - Testcontainers → [testcontainers.instructions.md](./instructions/testcontainers.instructions.md)
   - BDD (Cucumber) → [bdd.instructions.md](./instructions/bdd.instructions.md)
   - Versão Java específica → [java-11](./instructions/java-11.instructions.md) | [java-17](./instructions/java-17.instructions.md) | [java-21](./instructions/java-21.instructions.md) | [java-25](./instructions/java-25.instructions.md)
5. Para scaffold Micronaut → execute o prompt [scaffold-service](./prompts/scaffold-service.prompt.md)
6. Para migração de versão Java → execute [migrate-java-version](./prompts/migrate-java-version.prompt.md)
7. Consulte os templates em [assets/](./assets/)

## Integração com GitHub MCP

```
"Busque o template em {owner}/{repo}/path/to/Service.java usando o GitHub MCP"
```
