---
name: kotlin-patterns
description: "Kotlin patterns, templates and best practices. Use when scaffolding Spring Boot services, Ktor routes, working with Coroutines/Flow, reviewing Kotlin code, or looking for idiomatic Kotlin examples."
argument-hint: "Describe what you want to build or review (e.g. 'REST service with Spring Boot', 'Ktor route with auth', 'Flow pipeline')"
---

# Kotlin Patterns

Skill para boas práticas, scaffold e revisão de código Kotlin com frameworks modernos.

## Quando usar

- Scaffoldar um novo serviço Spring Boot em Kotlin
- Criar rotas e plugins Ktor
- Trabalhar com Coroutines, Flow, StateFlow
- Revisar código Kotlin em busca de padrões não-idiomáticos
- Buscar templates de código prontos para uso

## Frameworks cobertos

| Framework | Descrição |
|-----------|-----------|
| Spring Boot | Serviços REST, Data JPA, Security, Actuator |
| Ktor | HTTP server/client, routing, plugins, auth |
| Coroutines/Flow | Async, streams reativos, structured concurrency |
| AWS SDK for Kotlin | S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge (coroutine-native) |

## Procedimento

1. Identifique o framework alvo (Spring Boot, Ktor, Coroutines/Flow ou AWS)
2. Carregue as instruções específicas do framework:
   - Spring Boot → [spring-boot.instructions.md](./instructions/spring-boot.instructions.md)
   - Ktor → [ktor.instructions.md](./instructions/ktor.instructions.md)
   - Coroutines/Flow → [coroutines-flow.instructions.md](./instructions/coroutines-flow.instructions.md)
   - AWS SDK for Kotlin → [aws-sdk-kotlin.instructions.md](./instructions/aws-sdk-kotlin.instructions.md)
3. Para scaffold de serviço completo → execute o prompt [scaffold-service](./prompts/scaffold-service.prompt.md)
4. Para revisão de código com coroutines → execute o prompt [review-coroutines](./prompts/review-coroutines.prompt.md)
5. Para trabalho especializado em Ktor → delegue ao agente [ktor-specialist](./agents/ktor-specialist.agent.md)
6. Consulte os templates em [assets/](./assets/) como ponto de partida

## Templates disponíveis

- [spring-boot-service.kt](./assets/spring-boot-service.kt) — serviço REST com Spring Boot + Coroutines
- [ktor-route.kt](./assets/ktor-route.kt) — rota Ktor com autenticação e serialização

## Integração com GitHub MCP

Para buscar templates de repositórios externos, peça ao agente:

```
"Busque o template em {owner}/{repo}/path/to/file.kt usando o GitHub MCP"
```

O agente usará `github/get_file_contents` se o MCP estiver configurado.
