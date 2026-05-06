---
name: pydantic-model
description: "Define a Pydantic model with validators, computed fields, and serialization config from a natural language description of the domain entity."
---

Defina um modelo Pydantic completo para o seguinte contexto:

**Nome do modelo**: $model_name (ex: User, Order, PaymentMethod)
**Campos**: $fields (descreva nome, tipo e restrições de cada campo)
**Validações especiais**: $validations (ex: email único, valor positivo, datas consistentes)
**Contexto de uso**: $context (request de entrada | response de saída | ambos | modelo de configuração)
**Pydantic version**: $version (v2 — padrão | v1 legado)

## O que gerar

1. **Modelo base** com todos os campos tipados
2. **field_validator** para cada validação de campo individual
3. **model_validator** se houver validação que envolve múltiplos campos
4. **model_config** adequado ao contexto:
   - Request: `str_strip_whitespace = True`, `strict = True`
   - Response com ORM: `from_attributes = True`
   - Config: `env_file`, `env_file_encoding`
5. **Exemplos** via `model_config["json_schema_extra"]` para documentação OpenAPI
6. **Testes unitários** com casos válidos e inválidos

## Regras

- Use `EmailStr` para emails, `HttpUrl` para URLs, `UUID` para IDs
- Use `Annotated[str, Field(min_length=1, max_length=100)]` para restrições de string
- Use `model_validator(mode="after")` para validações cross-field
- Siga as boas práticas em [python.instructions.md](../../instructions/python.instructions.md)

## Exemplo de output

```python
from pydantic import BaseModel, EmailStr, field_validator, model_validator
from typing import Annotated
from pydantic import Field

class $model_name(BaseModel):
    # campos gerados aqui
    ...
```
