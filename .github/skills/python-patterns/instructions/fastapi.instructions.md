---
description: "Use when building FastAPI applications in Python. Covers router and endpoint patterns, Pydantic models, dependency injection, async database access, error handling, and testing with pytest."
---

# FastAPI — Padrões

## Estrutura do projeto

```
myservice/
├── app/
│   ├── main.py              # FastAPI app factory
│   ├── dependencies.py      # Depends() providers
│   ├── users/
│   │   ├── router.py        # APIRouter
│   │   ├── schemas.py       # Pydantic models
│   │   ├── service.py       # business logic
│   │   ├── repository.py    # data access
│   │   └── exceptions.py    # domain exceptions
│   └── core/
│       ├── config.py        # Settings com Pydantic
│       └── database.py      # async session factory
└── tests/
```

## App factory

```python
from fastapi import FastAPI
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    await database.connect()
    yield
    # shutdown
    await database.disconnect()

def create_app() -> FastAPI:
    app = FastAPI(title="My Service", lifespan=lifespan)
    app.include_router(users_router, prefix="/api/v1")
    app.add_exception_handler(UserNotFoundError, user_not_found_handler)
    return app
```

## Routers e endpoints

```python
from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.dependencies import get_db, get_current_user

router = APIRouter(prefix="/users", tags=["users"])

@router.get("/{user_id}", response_model=UserResponse)
async def get_user(
    user_id: UUID,
    service: UserService = Depends(get_user_service),
) -> UserResponse:
    return await service.find_by_id(user_id)

@router.post("", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def create_user(
    request: CreateUserRequest,
    service: UserService = Depends(get_user_service),
) -> UserResponse:
    return await service.create(request)

@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_user(
    user_id: UUID,
    current_user: User = Depends(get_current_user),
    service: UserService = Depends(get_user_service),
) -> None:
    await service.delete(user_id)
```

## Dependency Injection

```python
# dependencies.py
from collections.abc import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import async_session_factory

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise

def get_user_service(db: AsyncSession = Depends(get_db)) -> UserService:
    return UserService(UserRepository(db))
```

## Schemas Pydantic

```python
from pydantic import BaseModel, EmailStr, field_validator, model_validator
from uuid import UUID

class CreateUserRequest(BaseModel):
    name: str
    email: EmailStr

    @field_validator("name")
    @classmethod
    def name_not_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("name cannot be blank")
        return v.strip()

class UserResponse(BaseModel):
    id: UUID
    name: str
    email: str

    model_config = {"from_attributes": True}  # SQLAlchemy compat
```

## Tratamento de Erros

```python
# exceptions.py
class UserNotFoundError(Exception):
    def __init__(self, user_id: UUID) -> None:
        super().__init__(f"User '{user_id}' not found")
        self.user_id = user_id

# main.py
from fastapi import Request
from fastapi.responses import JSONResponse

async def user_not_found_handler(request: Request, exc: UserNotFoundError) -> JSONResponse:
    return JSONResponse(
        status_code=404,
        content={"code": "USER_NOT_FOUND", "message": str(exc)},
    )
```

## Configurações com Pydantic Settings

```python
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    database_url: str
    secret_key: str
    debug: bool = False

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

settings = Settings()
```

## Testes com pytest + httpx

```python
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import create_app

@pytest.fixture
def app():
    return create_app()

@pytest.mark.asyncio
async def test_create_user(app):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post("/api/v1/users", json={"name": "Alice", "email": "alice@example.com"})
    assert response.status_code == 201
    assert response.json()["name"] == "Alice"
```
