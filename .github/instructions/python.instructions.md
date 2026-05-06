---
description: "Use when writing or reviewing Python code. Covers type hints, Pydantic models, async patterns, dependency injection, FastAPI and Django idioms."
applyTo: "**/*.py"
---

# Python — Boas Práticas

## Type Hints

- Anote todos os parâmetros e retornos de funções públicas — sem exceções
- Use `from __future__ import annotations` para postponed evaluation (forward references)
- Prefira `X | None` a `Optional[X]` (Python 3.10+)
- Use `TypeAlias` para aliases complexos e `TypeVar` para genéricos
- Use `Protocol` para duck typing tipado — evita acoplamento via herança

```python
from __future__ import annotations
from typing import Protocol

class UserRepository(Protocol):
    async def find_by_id(self, user_id: str) -> User | None: ...

def process(users: list[User]) -> dict[str, User]:
    return {u.id: u for u in users}
```

## Dataclasses e Pydantic

- Use `@dataclass(frozen=True)` para value objects imutáveis simples
- Use **Pydantic** `BaseModel` para DTOs com validação de entrada (API, configs)
- Use `model_validator` e `field_validator` para validações de negócio em Pydantic
- Separe modelos de entrada (`CreateUserRequest`) de modelos de domínio (`User`) de saída (`UserResponse`)

```python
from pydantic import BaseModel, field_validator, EmailStr

class CreateUserRequest(BaseModel):
    name: str
    email: EmailStr

    @field_validator("name")
    @classmethod
    def name_must_not_be_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("name cannot be blank")
        return v.strip()
```

## Async/Await

- Use `async def` para toda operação de I/O (DB, HTTP, filesystem)
- Use `await asyncio.gather()` para concorrência de múltiplas coroutines
- Nunca chame funções sync bloqueantes dentro de `async def` — use `loop.run_in_executor()`
- Use `anyio` ou `asyncio.timeout()` para limitar duração de operações assíncronas
- Prefira `async with` e `async for` para gerenciar recursos assíncronos

```python
# Ruim — bloqueia o event loop
async def get_user(user_id: str) -> User:
    return db.query(User).filter_by(id=user_id).first()  # sync!

# Bom
async def get_user(user_id: str) -> User:
    async with get_session() as session:
        result = await session.get(User, user_id)
        if result is None:
            raise UserNotFoundError(user_id)
        return result
```

## Tratamento de Erros

- Crie exceções de domínio específicas herdando de `Exception`
- Nunca capture `Exception` genérico sem re-raise ou logging estruturado
- Use `contextlib.suppress(SpecificError)` somente para erros verdadeiramente ignoráveis
- Em FastAPI: use `HTTPException` nos handlers; nunca deixe exceções de domínio vazar para a camada HTTP

```python
class UserNotFoundError(Exception):
    def __init__(self, user_id: str) -> None:
        super().__init__(f"User '{user_id}' not found")
        self.user_id = user_id
```

## Dependency Injection

- Em FastAPI, use `Depends()` para injeção declarativa de dependências
- Separe a definição da dependência (`get_db`, `get_current_user`) de sua implementação
- Use `yield` em dependências para gerenciar lifecycle (open/close de conexões)

```python
async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise

@router.get("/users/{user_id}")
async def get_user(user_id: str, db: AsyncSession = Depends(get_db)) -> UserResponse:
    ...
```

## Estrutura de Módulos

- Prefira pacotes (`__init__.py`) a módulos únicos gigantes
- Evite imports circulares — reorganize se ocorrer
- Use imports absolutos: `from app.users.service import UserService`
- Organize por feature/domínio, não por tipo técnico (`users/`, `orders/` vs `models/`, `views/`)

## Convenções de Código

- Siga PEP 8 — use `ruff` para lint e `black` para formatação
- Nomes: `snake_case` para funções/variáveis, `PascalCase` para classes, `UPPER_CASE` para constantes
- Docstrings: use estilo Google ou NumPy para funções públicas
- Prefira `pathlib.Path` a `os.path` para manipulação de caminhos
- Use `logging` estruturado — nunca `print()` em produção
