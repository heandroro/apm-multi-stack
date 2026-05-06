---
description: "Use when writing Python code that integrates with AWS. Covers boto3 (sync) and aioboto3 (async) usage rules, credential handling, and when to use each client type."
applyTo: "**/*.py"
---

# Python + AWS (boto3 / aioboto3) — Regras de Uso

## boto3 vs aioboto3 — quando usar cada um

| Contexto | Use |
|----------|-----|
| FastAPI, asyncio, `async def` | `aioboto3` — nunca `boto3` bloqueante |
| Django, Flask, scripts síncronos | `boto3` |
| Lambda handler (síncrono) | `boto3` |

> Usar `boto3` bloqueante dentro de um event loop asyncio trava o servidor.
> Usar `aioboto3` em handler síncrono causa erro de loop.

## Padrão obrigatório — context manager por operação

```python
# CORRETO — crie o cliente dentro do context manager
async with session.client("s3") as s3:
    await s3.put_object(Bucket=bucket, Key=key, Body=body)

# ERRADO — cliente aioboto3 não é thread-safe nem reutilizável fora do CM
self._s3 = await session.client("s3").__aenter__()  # NÃO faça isso
```

## Configuração de credenciais

```python
# Sync — resolução automática (env vars → ~/.aws → IAM role)
import boto3
client = boto3.client("s3", region_name="us-east-1")

# Async
import aioboto3
session = aioboto3.Session()  # singleton — reutilize via DI

# Nunca hardcode — ERRADO:
boto3.client("s3", aws_access_key_id="AKIA...", aws_secret_access_key="...")
```

Ordem de resolução de credenciais:
1. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_DEFAULT_REGION` (env vars)
2. `~/.aws/credentials` (desenvolvimento local via `aws configure`)
3. IAM Role (EC2 / ECS task role / Lambda execution role) — **usar em produção**

## Injeção de dependência com FastAPI

```python
from functools import lru_cache
import aioboto3

@lru_cache(maxsize=1)
def get_aws_session() -> aioboto3.Session:
    return aioboto3.Session()

# No endpoint — injete via Depends
async def upload(
    file: UploadFile,
    session: aioboto3.Session = Depends(get_aws_session),
) -> None:
    async with session.client("s3") as s3:
        await s3.put_object(...)
```

## Tratamento de erros

```python
from botocore.exceptions import ClientError

try:
    response = await s3.get_object(Bucket=bucket, Key=key)
except ClientError as e:
    code = e.response["Error"]["Code"]
    if code == "NoSuchKey":
        raise FileNotFoundError(f"{key} not found") from e
    if code == "NoSuchBucket":
        raise ValueError(f"Bucket {bucket} does not exist") from e
    raise  # re-raise erros inesperados
```

## DynamoDB — use `Decimal`, não `float`

```python
from decimal import Decimal

# CORRETO — boto3 serializa Decimal para Number sem perda de precisão
item = {"price": Decimal("49.99")}

# ERRADO — boto3 levanta TypeError para float
item = {"price": 49.99}
```

## Secrets Manager — nunca logue o valor

```python
response = await sm.get_secret_value(SecretId=secret_id)
# Logue APENAS o id — nunca o SecretString
logger.info("secret loaded", extra={"secret_id": secret_id})
```

## Type stubs — obrigatórios para mypy

```toml
# pyproject.toml
boto3-stubs = {extras = ["s3","sqs","sns","dynamodb","secretsmanager","events"], version = ">=1.35"}
types-aiobotocore = {extras = ["s3","sqs","sns","dynamodb","secretsmanager","events"], version = ">=2.15"}
```

## Exemplos completos

Para padrões detalhados com S3, SQS, SNS, DynamoDB, Secrets Manager e EventBridge,
use o skill `/python-patterns` → [aws-sdk-python.instructions.md](../skills/python-patterns/instructions/aws-sdk-python.instructions.md).
