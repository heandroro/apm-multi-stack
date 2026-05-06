---
description: "Use when building HTTP APIs with Gin in Go. Covers router setup, handler patterns, middleware, binding/validation, error handling, and testing."
---

# Gin — Padrões

## Setup do router

```go
package main

import (
    "context"
    "log/slog"
    "net/http"
    "os"
    "os/signal"
    "syscall"
    "time"

    "github.com/gin-gonic/gin"
)

func main() {
    router := gin.New()
    router.Use(gin.Recovery())
    router.Use(requestLogger())

    v1 := router.Group("/api/v1")
    RegisterUserRoutes(v1, NewUserHandler(/* deps */))

    srv := &http.Server{Addr: ":8080", Handler: router}

    // Graceful shutdown
    go func() {
        if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
            slog.Error("server error", "err", err)
            os.Exit(1)
        }
    }()

    quit := make(chan os.Signal, 1)
    signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
    <-quit

    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()
    srv.Shutdown(ctx)
}
```

## Handlers

- Handlers são métodos de struct — não funções soltas
- Use `c.ShouldBindJSON` para deserializar (retorna erro, não panic)
- Use `c.JSON` para responder — sempre com status code explícito
- Extraia lógica de negócio para o service — handler apenas roteia

```go
type UserHandler struct {
    service UserService
}

func NewUserHandler(service UserService) *UserHandler {
    return &UserHandler{service: service}
}

func (h *UserHandler) Create(c *gin.Context) {
    var req CreateUserRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, ErrorResponse{Code: "VALIDATION_ERROR", Message: err.Error()})
        return
    }

    user, err := h.service.Create(c.Request.Context(), req)
    if err != nil {
        handleServiceError(c, err)
        return
    }

    c.JSON(http.StatusCreated, user)
}

func (h *UserHandler) FindByID(c *gin.Context) {
    id := c.Param("id")
    user, err := h.service.FindByID(c.Request.Context(), id)
    if err != nil {
        handleServiceError(c, err)
        return
    }
    c.JSON(http.StatusOK, user)
}
```

## Registro de rotas

```go
func RegisterUserRoutes(rg *gin.RouterGroup, h *UserHandler) {
    users := rg.Group("/users")
    users.POST("", h.Create)
    users.GET("/:id", h.FindByID)
    users.DELETE("/:id", AuthMiddleware(), h.Delete)
}
```

## Binding e Validação

```go
type CreateUserRequest struct {
    Name  string `json:"name"  binding:"required,min=2,max=100"`
    Email string `json:"email" binding:"required,email"`
}
```

## Middleware

```go
func AuthMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        token := c.GetHeader("Authorization")
        if token == "" {
            c.AbortWithStatusJSON(http.StatusUnauthorized, ErrorResponse{Code: "UNAUTHORIZED"})
            return
        }
        // valida token, extrai claims
        c.Set("userID", claims.UserID)
        c.Next()
    }
}

func requestLogger() gin.HandlerFunc {
    return func(c *gin.Context) {
        start := time.Now()
        c.Next()
        slog.Info("request",
            "method", c.Request.Method,
            "path", c.Request.URL.Path,
            "status", c.Writer.Status(),
            "latency", time.Since(start),
        )
    }
}
```

## Error Handling centralizado

```go
func handleServiceError(c *gin.Context, err error) {
    var notFound *NotFoundError
    switch {
    case errors.As(err, &notFound):
        c.JSON(http.StatusNotFound, ErrorResponse{Code: "NOT_FOUND", Message: err.Error()})
    default:
        slog.Error("internal error", "err", err)
        c.JSON(http.StatusInternalServerError, ErrorResponse{Code: "INTERNAL_ERROR"})
    }
}
```
