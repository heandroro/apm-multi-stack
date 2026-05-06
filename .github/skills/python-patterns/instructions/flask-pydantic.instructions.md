---
description: "Use when building Flask applications in Python with Pydantic for validation. Covers Blueprint structure, request validation with Pydantic, error handling, and testing with pytest."
---

# Flask + Pydantic — Padrões

## Estrutura com Blueprints

```python
# app/__init__.py
from flask import Flask
from app.users.routes import users_bp
from app.core.errors import register_error_handlers

def create_app() -> Flask:
    app = Flask(__name__)
    app.config.from_object("app.core.config.Config")
    app.register_blueprint(users_bp, url_prefix="/api/v1")
    register_error_handlers(app)
    return app
```

## Modelos Pydantic para validação

```python
# app/users/schemas.py
from pydantic import BaseModel, EmailStr, field_validator
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

    model_config = {"from_attributes": True}
```

## Routes com Blueprint

```python
# app/users/routes.py
from flask import Blueprint, request, jsonify
from pydantic import ValidationError
from app.users.schemas import CreateUserRequest, UserResponse
from app.users.service import UserService

users_bp = Blueprint("users", __name__)
service = UserService()

@users_bp.get("/users/<uuid:user_id>")
def get_user(user_id):
    user = service.find_by_id(user_id)
    return jsonify(UserResponse.model_validate(user).model_dump(mode="json")), 200

@users_bp.post("/users")
def create_user():
    try:
        req = CreateUserRequest.model_validate(request.get_json())
    except ValidationError as e:
        return jsonify({"code": "VALIDATION_ERROR", "detail": e.errors()}), 400

    user = service.create(req)
    return jsonify(UserResponse.model_validate(user).model_dump(mode="json")), 201
```

## Tratamento global de erros

```python
# app/core/errors.py
from flask import Flask, jsonify
from app.users.exceptions import UserNotFoundError

def register_error_handlers(app: Flask) -> None:

    @app.errorhandler(UserNotFoundError)
    def handle_not_found(exc: UserNotFoundError):
        return jsonify({"code": "USER_NOT_FOUND", "message": str(exc)}), 404

    @app.errorhandler(404)
    def handle_404(exc):
        return jsonify({"code": "NOT_FOUND", "message": "Resource not found"}), 404

    @app.errorhandler(500)
    def handle_500(exc):
        return jsonify({"code": "INTERNAL_ERROR", "message": "Internal server error"}), 500
```

## Configuração

```python
# app/core/config.py
import os

class Config:
    SECRET_KEY: str = os.environ["SECRET_KEY"]
    DATABASE_URL: str = os.environ["DATABASE_URL"]
    DEBUG: bool = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
```

## Testes

```python
import pytest
from app import create_app

@pytest.fixture
def client():
    app = create_app()
    app.config["TESTING"] = True
    with app.test_client() as client:
        yield client

def test_create_user(client):
    response = client.post(
        "/api/v1/users",
        json={"name": "Alice", "email": "alice@example.com"},
    )
    assert response.status_code == 201
    data = response.get_json()
    assert data["name"] == "Alice"

def test_create_user_invalid_email(client):
    response = client.post("/api/v1/users", json={"name": "Alice", "email": "not-an-email"})
    assert response.status_code == 400
    assert response.get_json()["code"] == "VALIDATION_ERROR"
```
