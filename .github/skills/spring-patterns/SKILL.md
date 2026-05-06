---
name: spring-patterns
description: "Spring Framework patterns, templates and best practices. Use when building or reviewing Spring Boot 3.x/4.x applications, integrating with AWS via Spring Cloud AWS 3.x, migrating between Spring Boot major versions, or looking for idiomatic Spring patterns."
argument-hint: "Describe what you want to build or review (e.g. 'REST API with Spring Boot', 'SQS consumer with Spring Cloud AWS', 'migrate Spring Boot 3 to 4', 'Spring Security config')"
---

# Spring Patterns

Skill para boas práticas, scaffold e revisão de aplicações Spring Boot e Spring Cloud AWS.

## Frameworks e tópicos cobertos

| Tópico | Descrição |
|--------|-----------|
| Spring Boot 3.x | REST, Data JPA, Security, Actuator, Observability |
| Spring Boot 4.x | Jakarta EE 11, Spring Framework 7, Virtual Threads padrão |
| Spring Cloud AWS 3.x | S3, SQS, SNS, DynamoDB, Secrets Manager, Parameter Store, EventBridge, ElastiCache |
| Spring Security | `SecurityFilterChain`, OAuth2 Resource Server, JWT |
| Spring Data JPA | Entidades, repositórios, projeções, paginação, auditing, Testcontainers |
| Testcontainers | `@ServiceConnection`, `@DynamicPropertySource`, `@DataJpaTest` com banco real, Kafka, LocalStack |
| Testing (slices) | `@WebMvcTest`, `@DataJpaTest`, `@RestClientTest`, `@SpringBootTest`, `@MockBean` |

## Quando usar

- Criar um novo serviço REST com Spring Boot
- Modelar entidades JPA, repositórios, projeções ou paginação
- Integrar um serviço Spring Boot com AWS (S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge)
- Migrar de Spring Boot 3.x para 4.x
- Migrar de AWS SDK v1 (`com.amazonaws`) / Spring Cloud AWS 2.x → SDK v2 / Spring Cloud AWS 3.x
- Revisar código Spring em busca de padrões não-idiomáticos
- Configurar Spring Security com OAuth2/JWT

## Procedimento

1. Identifique o tópico alvo
2. Carregue as instruções específicas:
   - Spring Boot → [spring-boot.instructions.md](./instructions/spring-boot.instructions.md)
   - Spring Cloud AWS → [spring-cloud-aws.instructions.md](./instructions/spring-cloud-aws.instructions.md)
   - Spring Data JPA → [spring-data-jpa.instructions.md](./instructions/spring-data-jpa.instructions.md)
   - Testcontainers → [testcontainers.instructions.md](./instructions/testcontainers.instructions.md)
   - Testing (slices) → [testing.instructions.md](./instructions/testing.instructions.md)
3. Para scaffold de um novo serviço Spring Boot → execute [scaffold-spring-service](./prompts/scaffold-spring-service.prompt.md)
4. Para migração de versão Spring Boot → execute [migrate-spring-boot](./prompts/migrate-spring-boot.prompt.md)
5. Para trabalho aprofundado → delegue ao agente [spring-specialist](./agents/spring-specialist.agent.md)
6. Consulte templates em [assets/](./assets/)

## Integração com GitHub MCP

```
"Busque o template em {owner}/{repo}/path/to/Service.java usando o GitHub MCP"
```

### Repositório de referência — Spring Boot 4 + Java 21/25

| Campo | Valor |
|-------|-------|
| owner | `heandroro` |
| repo  | `spring-boot-4-poc` |
| Arquitetura | Hexagonal (`domain/`, `application/`, `infrastructure/`, `web/`) |
| Java | 21 / 25 |
| Spring Boot | 4.x |

**Paths úteis:**

- `src/main/java/com/example/poc/web/` — controllers, DTOs `record`, `ProblemDetail`
- `src/main/java/com/example/poc/application/` — use cases, serviços com `@Transactional`
- `src/main/java/com/example/poc/domain/` — modelo de domínio, `record`, sealed classes
- `src/main/java/com/example/poc/infrastructure/` — repositórios JPA, adapters

**Como buscar:**
```
tool: github/get_file_contents
args: { owner: heandroro, repo: spring-boot-4-poc, path: "src/main/java/com/example/poc/web" }
```

