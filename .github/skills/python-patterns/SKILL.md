---
name: python-patterns
description: "Python design patterns, framework templates and best practices. Use when scaffolding FastAPI endpoints, Django views, Flask routes, defining Pydantic models, writing async Python, or reviewing Python code for idiomatic patterns."
argument-hint: "Describe what you want to build or review (e.g. 'FastAPI CRUD endpoint', 'Pydantic model with validation', 'Django REST view', 'async service layer')"
---

# Python Patterns

Skill para boas práticas, scaffold e revisão de código Python com frameworks modernos.

## Quando usar

- Scaffoldar endpoints FastAPI com validação Pydantic
- Criar views Django com DRF ou class-based views
- Implementar rotas Flask com Blueprint
- Definir modelos Pydantic com validadores customizados
- Revisar código Python em busca de type hints ausentes, bloqueio de event loop ou anti-patterns
- Buscar templates de projeto Python

## Frameworks cobertos

| Framework | Descrição |
|-----------|-----------|
| FastAPI | REST APIs assíncronas com Pydantic e OpenAPI automático |
| Django | Framework full-stack com ORM, admin, DRF |
| Flask + Pydantic | Microframework com validação tipada |
| boto3 / aioboto3 | AWS SDK — S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge |

## Quando usar

- Scaffoldar endpoints FastAPI com validação Pydantic
- Criar views Django com DRF ou class-based views
- Implementar rotas Flask com Blueprint
- Integrar serviço Python com AWS (S3, SQS, SNS, DynamoDB, Secrets Manager)
- Revisar código Python em busca de type hints ausentes, bloqueio de event loop ou anti-patterns

## Procedimento

1. Identifique o framework alvo
2. Carregue as instruções específicas:
   - FastAPI → [fastapi.instructions.md](./instructions/fastapi.instructions.md)
   - Django → [django.instructions.md](./instructions/django.instructions.md)
   - Flask + Pydantic → [flask-pydantic.instructions.md](./instructions/flask-pydantic.instructions.md)
   - AWS (boto3/aioboto3) → [aws-sdk-python.instructions.md](./instructions/aws-sdk-python.instructions.md)
3. Para scaffold de endpoint → execute o prompt [scaffold-endpoint](./prompts/scaffold-endpoint.prompt.md)
4. Para definição de modelos Pydantic → execute [pydantic-model](./prompts/pydantic-model.prompt.md)
5. Para trabalho especializado em FastAPI → delegue ao agente [fastapi-specialist](./agents/fastapi-specialist.agent.md)
6. Consulte os templates em [assets/](./assets/)

## Integração com GitHub MCP

```
"Busque o template em {owner}/{repo}/path/to/router.py usando o GitHub MCP"
```
