---
description: "Use when building HTTP APIs with Fiber in Go. Covers app setup, handler patterns, middleware, input parsing, error handling, and testing."
---

# Fiber — Padrões

## Setup da aplicação

```go
package main

import (
    "log/slog"
    "os"
    "os/signal"
    "syscall"

    "github.com/gofiber/fiber/v2"
    "github.com/gofiber/fiber/v2/middleware/logger"
    "github.com/gofiber/fiber/v2/middleware/recover"
)

func main() {
    app := fiber.New(fiber.Config{
        ErrorHandler: globalErrorHandler,
    })

    app.Use(recover.New())
    app.Use(logger.New())

    v1 := app.Group("/api/v1")
    RegisterUserRoutes(v1, NewUserHandler(/* deps */))

    quit := make(chan os.Signal, 1)
    signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

    go func() {
        if err := app.Listen(":8080"); err != nil {
            slog.Error("server error", "err", err)
        }
    }()

    <-quit
    app.Shutdown()
}
```

## Handlers

- Use `c.BodyParser(&req)` para deserializar o body
- Use `c.Params("name")` para path params, `c.Query("name")` para query string
- Retorne `c.JSON(status, body)` — Fiber retorna os erros via `ErrorHandler` global
- Use `c.Locals("key", value)` para passar dados entre middlewares

```go
type UserHandler struct {
    service UserService
}

func (h *UserHandler) Create(c *fiber.Ctx) error {
    var req CreateUserRequest
    if err := c.BodyParser(&req); err != nil {
        return fiber.NewError(fiber.StatusBadRequest, "invalid request body")
    }
    if err := validate.Struct(req); err != nil {
        return fiber.NewError(fiber.StatusBadRequest, err.Error())
    }

    user, err := h.service.Create(c.UserContext(), req)
    if err != nil {
        return err  // delegado ao ErrorHandler global
    }

    return c.Status(fiber.StatusCreated).JSON(user)
}

func (h *UserHandler) FindByID(c *fiber.Ctx) error {
    id := c.Params("id")
    user, err := h.service.FindByID(c.UserContext(), id)
    if err != nil {
        return err
    }
    return c.JSON(user)
}
```

## Context propagation

- Use `c.UserContext()` para obter o `context.Context` da requisição
- Use `c.SetUserContext(ctx)` em middleware para enriquecer o context (ex: tracing)

```go
func TracingMiddleware() fiber.Handler {
    return func(c *fiber.Ctx) error {
        ctx, span := tracer.Start(c.UserContext(), c.Route().Path)
        defer span.End()
        c.SetUserContext(ctx)
        return c.Next()
    }
}
```

## Registro de rotas

```go
func RegisterUserRoutes(rg fiber.Router, h *UserHandler) {
    users := rg.Group("/users")
    users.Post("", h.Create)
    users.Get("/:id", h.FindByID)
    users.Delete("/:id", AuthMiddleware(), h.Delete)
}
```

## Error Handler global

```go
func globalErrorHandler(c *fiber.Ctx, err error) error {
    code := fiber.StatusInternalServerError
    message := "internal server error"

    var fiberErr *fiber.Error
    var notFound *NotFoundError

    switch {
    case errors.As(err, &fiberErr):
        code = fiberErr.Code
        message = fiberErr.Message
    case errors.As(err, &notFound):
        code = fiber.StatusNotFound
        message = err.Error()
    default:
        slog.Error("unhandled error", "err", err)
    }

    return c.Status(code).JSON(ErrorResponse{Code: http.StatusText(code), Message: message})
}
```

## Validação com go-playground/validator

```go
import "github.com/go-playground/validator/v10"

var validate = validator.New()

type CreateUserRequest struct {
    Name  string `json:"name"  validate:"required,min=2,max=100"`
    Email string `json:"email" validate:"required,email"`
}
```
