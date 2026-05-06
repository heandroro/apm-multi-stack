---
name: go-patterns
description: "Go design patterns, framework templates and best practices. Use when scaffolding Gin or Fiber HTTP handlers, implementing gRPC services, reviewing idiomatic Go code, or working with goroutines and channels."
argument-hint: "Describe what you want to build or review (e.g. 'REST API with Gin', 'gRPC service with protobuf', 'middleware for Fiber')"
---

# Go Patterns

Skill para boas práticas, scaffold e revisão de código Go com frameworks e padrões modernos.

## Quando usar

- Scaffoldar handlers HTTP com Gin ou Fiber
- Definir e implementar serviços gRPC com protobuf
- Revisar código Go em busca de error handling incorreto ou goroutines sem controle
- Implementar middleware (logging, auth, tracing) de forma idiomática
- Buscar templates de estrutura de projetos Go

## Frameworks cobertos

| Framework | Descrição |
|-----------|-----------|
| Gin | HTTP router/framework leve, amplamente usado |
| Fiber | Framework inspirado em Express, alto desempenho |
| net/http | Standard library — sem dependências externas |
| gRPC + protobuf | RPC com tipagem forte e geração de código |
| AWS SDK for Go v2 | S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge |

## Procedimento

1. Identifique o framework/padrão alvo
2. Carregue as instruções específicas:
   - Gin → [gin.instructions.md](./instructions/gin.instructions.md)
   - Fiber → [fiber.instructions.md](./instructions/fiber.instructions.md)
   - gRPC/protobuf → [grpc-protobuf.instructions.md](./instructions/grpc-protobuf.instructions.md)
   - AWS SDK v2 → [aws-sdk-go.instructions.md](./instructions/aws-sdk-go.instructions.md)
3. Para scaffold de handler HTTP → execute o prompt [scaffold-handler](./prompts/scaffold-handler.prompt.md)
4. Para definição de `.proto` e implementação → execute [define-proto](./prompts/define-proto.prompt.md)
5. Para trabalho especializado em gRPC → delegue ao agente [grpc-specialist](./agents/grpc-specialist.agent.md)
6. Consulte os templates em [assets/](./assets/)

## Integração com GitHub MCP

```
"Busque o template em {owner}/{repo}/path/to/handler.go usando o GitHub MCP"
```
