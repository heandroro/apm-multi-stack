---
name: scaffold-handler
description: "Scaffold a Go HTTP handler with Gin or Fiber. Generates handler struct, route registration, service interface, error types, and test skeleton."
---

Crie um handler Go completo com base nas seguintes escolhas:

**Framework**: $framework (Gin | Fiber | net/http)
**Nome do recurso**: $resource (ex: User, Product, Order)
**Operações**: $operations (ex: CRUD completo | GET + POST | somente GET)
**Módulo Go**: $module (ex: github.com/myorg/myservice)

## O que gerar

1. **Tipos de domínio** — struct `$resource` e tipos de request/response
2. **Interface de service** — `${resource}Service` com as operações solicitadas
3. **Handler struct** — `${resource}Handler` com método por operação
4. **Registro de rotas** — função `Register${resource}Routes`
5. **Error types** — `${resource}NotFoundError`, implementando `error`
6. **Error handler** — mapeamento de domain errors para HTTP status codes
7. **Testes** — esqueleto de teste para cada handler com mock do service

## Regras

- Todos os handlers recebem `context.Context` da requisição (`c.Request.Context()` no Gin, `c.UserContext()` no Fiber)
- Erros são retornados como valores — nunca `panic`
- Use `log/slog` para logging estruturado
- Siga as boas práticas em [go.instructions.md](../../instructions/go.instructions.md)
- Para Gin: siga [gin.instructions.md](../instructions/gin.instructions.md)
- Para Fiber: siga [fiber.instructions.md](../instructions/fiber.instructions.md)

## Output esperado

Arquivos Go com package correto, imports organizados, sem dependências não declaradas.
Inclua o comando `go get` necessário para as dependências do framework escolhido.
