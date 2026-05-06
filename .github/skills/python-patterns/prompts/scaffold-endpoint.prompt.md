---
name: scaffold-endpoint
description: "Scaffold a Python REST endpoint with FastAPI, Django DRF, or Flask+Pydantic. Generates schema, service, repository, router, and test skeleton."
---

Crie um endpoint Python completo com base nas seguintes escolhas:

**Framework**: $framework (FastAPI | Django DRF | Flask + Pydantic)
**Nome do recurso**: $resource (ex: User, Product, Order)
**Operações**: $operations (ex: CRUD completo | GET + POST | somente GET)
**Banco de dados**: $database (ex: PostgreSQL com SQLAlchemy async | Django ORM | sem banco)

## O que gerar

1. **Pydantic schemas** — `Create${resource}Request` com validadores, `${resource}Response`
2. **Domain model** — classe ou SQLAlchemy model para `$resource`
3. **Repository** — funções async de acesso a dados
4. **Service** — `${resource}Service` com lógica de negócio, tratamento de `None`
5. **Router / ViewSet / Blueprint** — endpoints com status codes corretos
6. **Exceptions** — `${resource}NotFoundError`, handler HTTP correspondente
7. **Testes** — fixtures pytest e casos de teste para operações geradas

## Regras

- Use `async def` para todas as operações com I/O
- Use `UUID` como tipo de ID
- Type-hint todos os parâmetros e retornos
- Nunca retorne `None` sem documentar — use `| None` no tipo de retorno
- Siga as boas práticas em [python.instructions.md](../../instructions/python.instructions.md)
- Para FastAPI: siga [fastapi.instructions.md](../instructions/fastapi.instructions.md)
- Para Django: siga [django.instructions.md](../instructions/django.instructions.md)
- Para Flask: siga [flask-pydantic.instructions.md](../instructions/flask-pydantic.instructions.md)

## Output esperado

Arquivos `.py` por componente, com imports corretos e `requirements.txt` / `pyproject.toml` snippet.
