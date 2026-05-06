---
description: "Use when writing or reviewing Go code. Covers idiomatic error handling, interfaces, goroutines, context propagation, package design, and gRPC/HTTP patterns."
applyTo: "**/*.go"
---

# Go — Boas Práticas

## Error Handling

- Erros são valores — retorne `(T, error)` e verifique sempre
- Use `fmt.Errorf("operação %s: %w", id, err)` para wrapping com contexto (sentinel via `errors.Is`)
- Use `errors.Is()` para comparar erros (não `==`) e `errors.As()` para converter tipo
- Crie sentinel errors com `var ErrNotFound = errors.New("not found")` para erros identificáveis
- Crie tipos de erro customizados apenas quando o chamador precisa inspecionar campos do erro

```go
// Ruim
if err != nil {
    return err  // perde o contexto
}

// Bom
if err != nil {
    return fmt.Errorf("fetch user %s: %w", id, err)
}
```

## Interfaces

- Defina interfaces **no pacote consumidor**, não no produtor (duck typing)
- Prefira interfaces pequenas — uma ou duas funções (Interface Segregation)
- Interfaces implícitas: não declare `implements` — a satisfação é automática
- Use `interface{}` / `any` somente quando tipos genéricos não resolvem

```go
// Definido no pacote que consome, não em "user/repository"
type UserFinder interface {
    FindByID(ctx context.Context, id string) (*User, error)
}
```

## Goroutines e Concorrência

- Nunca inicie uma goroutine sem um mecanismo de encerramento (context, channel, WaitGroup)
- Documente quem é dono de cada goroutine e quando ela termina
- Use `sync.WaitGroup` para aguardar grupos de goroutines
- Use `sync.Mutex` / `sync.RWMutex` para proteger estado compartilhado
- Prefira channels para comunicação e mutexes para estado — não misture

```go
// Goroutine com cancelamento via context
go func() {
    select {
    case <-ctx.Done():
        return
    case result := <-work:
        process(result)
    }
}()
```

## Context Propagation

- Sempre passe `context.Context` como **primeiro parâmetro** de funções que fazem I/O
- Nunca armazene `context.Context` em structs — passe explicitamente
- Use `context.WithTimeout` / `context.WithDeadline` para limitar duração de operações
- Use `context.WithValue` apenas para dados cross-cutting (trace ID, auth) — não para parâmetros de negócio

```go
func (s *UserService) FindByID(ctx context.Context, id string) (*User, error) {
    ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
    defer cancel()
    return s.repo.FindByID(ctx, id)
}
```

## Package Design

- Um pacote = uma responsabilidade coesa. Evite pacotes genéricos como `util`, `common`, `helpers`
- Nomes de pacotes: curtos, minúsculos, sem underscores (`userservice` → `user`)
- Exporte apenas o necessário — comece com tudo unexported e exporte sob demanda
- Evite dependências cíclicas entre pacotes — use interfaces para quebrar ciclos

## Structs e Métodos

- Use struct embedding para composição — não herança
- Métodos com receiver ponteiro (`*T`) modificam o estado; com valor (`T`) leem apenas
- Use construtores `NewX(...)` para validação e inicialização de structs complexos
- Prefira `struct{}` como sentinela em maps/sets: `map[string]struct{}`

## HTTP Handlers (padrão net/http)

- Handlers são funções `func(w http.ResponseWriter, r *http.Request)`
- Use `r.Context()` para propagar o context da requisição
- Retorne erros com `http.Error()` ou escreva JSON estruturado — nunca panic
- Use middleware para cross-cutting concerns (logging, auth, tracing)

## Convenções de Código

- Siga `gofmt` / `goimports` — sem debate de estilo
- Use `golangci-lint` com regras `errcheck`, `govet`, `staticcheck`
- Nomes de variáveis: camelCase curto dentro de funções (`u` para `user`, `err` para erros)
- Exported names: sempre com comentário GoDoc (`// UserService handles...`)
- Evite `init()` — prefira construtores explícitos
