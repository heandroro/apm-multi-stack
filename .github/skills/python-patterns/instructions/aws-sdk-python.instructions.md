---
description: "Use when integrating Python services with AWS. Covers boto3 (sync) and aioboto3 (async, for FastAPI/asyncio) for S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge. Includes type stubs, moto for unit tests, and Testcontainers for integration tests."
---

# AWS SDK para Python (boto3 / aioboto3) — Boas Práticas

> Use **boto3** para código síncrono (Django, Flask, scripts) e **aioboto3** para código assíncrono (FastAPI, asyncio).
> Nunca use `aioboto3` em handlers síncronos nem `boto3` bloqueante dentro de `async def` sem thread executor.

## Dependências

```toml
# pyproject.toml (uv / poetry)
[project.dependencies]
boto3      = ">=1.35"
aioboto3   = ">=13.0"   # wrapper async sobre boto3

# Type stubs — essencial para autocompletar e mypy
boto3-stubs = {extras = ["s3","sqs","sns","dynamodb","secretsmanager","events"], version = ">=1.35"}
types-aiobotocore = {extras = ["s3","sqs","sns","dynamodb","secretsmanager","events"], version = ">=2.15"}

[project.optional-dependencies]
test = [
    "moto[s3,sqs,sns,dynamodb,secretsmanager,events]>=5.0",
    "testcontainers[localstack]>=4.8",
]
```

## Configuração de credenciais e região

O boto3 usa a mesma **credential provider chain** do AWS SDK v2:
1. Variáveis de ambiente `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_DEFAULT_REGION`
2. `~/.aws/credentials` (desenvolvimento local)
3. IAM Role (EC2 instance profile / ECS task role / Lambda) — **preferido em produção**

```python
# Configuração recomendada — use variáveis de ambiente ou IAM role
import boto3

# Sync — deixe o SDK resolver credenciais automaticamente
s3_client = boto3.client("s3", region_name="us-east-1")

# Async (FastAPI)
import aioboto3
session = aioboto3.Session()
```

> **Regra de segurança**: nunca passe `aws_access_key_id` / `aws_secret_access_key` hardcoded no código. Use variáveis de ambiente ou IAM roles.

---

## Injeção de dependência com FastAPI

```python
# dependencies/aws.py
from functools import lru_cache
import aioboto3
from mypy_extensions import TypedDict

@lru_cache(maxsize=1)
def get_aws_session() -> aioboto3.Session:
    return aioboto3.Session()

# Em cada endpoint — crie o cliente dentro do context manager
# NÃO guarde o cliente como singleton — aioboto3 usa context managers
```

---

## S3

### Assíncrono (FastAPI)

```python
# services/storage.py
from contextlib import asynccontextmanager
from typing import AsyncIterator
import aioboto3
from botocore.exceptions import ClientError

class S3StorageService:
    def __init__(self, session: aioboto3.Session, bucket: str) -> None:
        self._session = session
        self._bucket = bucket

    async def upload(self, key: str, body: bytes, content_type: str) -> None:
        async with self._session.client("s3") as s3:
            await s3.put_object(
                Bucket=self._bucket,
                Key=key,
                Body=body,
                ContentType=content_type,
            )

    async def download(self, key: str) -> bytes:
        async with self._session.client("s3") as s3:
            try:
                response = await s3.get_object(Bucket=self._bucket, Key=key)
                return await response["Body"].read()
            except ClientError as e:
                if e.response["Error"]["Code"] == "NoSuchKey":
                    raise FileNotFoundError(f"Object {key} not found") from e
                raise

    async def delete(self, key: str) -> None:
        async with self._session.client("s3") as s3:
            await s3.delete_object(Bucket=self._bucket, Key=key)

    async def presign_download(self, key: str, expires_in: int = 900) -> str:
        async with self._session.client("s3") as s3:
            return await s3.generate_presigned_url(
                "get_object",
                Params={"Bucket": self._bucket, "Key": key},
                ExpiresIn=expires_in,
            )
```

---

## SQS

```python
# services/queue.py
from dataclasses import dataclass
from typing import AsyncIterator
import json
import aioboto3

@dataclass
class Message:
    body: str
    receipt_handle: str
    message_id: str

class SQSService:
    def __init__(self, session: aioboto3.Session, queue_url: str) -> None:
        self._session = session
        self._queue_url = queue_url

    async def send(self, payload: dict) -> None:
        async with self._session.client("sqs") as sqs:
            await sqs.send_message(
                QueueUrl=self._queue_url,
                MessageBody=json.dumps(payload),
            )

    async def receive(self, max_messages: int = 10) -> list[Message]:
        async with self._session.client("sqs") as sqs:
            response = await sqs.receive_message(
                QueueUrl=self._queue_url,
                MaxNumberOfMessages=max_messages,
                WaitTimeSeconds=20,  # long polling
            )
            return [
                Message(
                    body=msg["Body"],
                    receipt_handle=msg["ReceiptHandle"],
                    message_id=msg["MessageId"],
                )
                for msg in response.get("Messages", [])
            ]

    async def delete(self, receipt_handle: str) -> None:
        async with self._session.client("sqs") as sqs:
            await sqs.delete_message(
                QueueUrl=self._queue_url,
                ReceiptHandle=receipt_handle,
            )
```

---

## SNS

```python
class SNSService:
    def __init__(self, session: aioboto3.Session, topic_arn: str) -> None:
        self._session = session
        self._topic_arn = topic_arn

    async def publish(self, message: str, subject: str | None = None) -> str:
        async with self._session.client("sns") as sns:
            response = await sns.publish(
                TopicArn=self._topic_arn,
                Message=message,
                **({"Subject": subject} if subject else {}),
            )
            return response["MessageId"]
```

---

## DynamoDB

```python
# Prefira o DynamoDB resource (alto nível) ao client (baixo nível)
from decimal import Decimal
from typing import Any

class OrderItemRepository:
    def __init__(self, session: aioboto3.Session, table_name: str) -> None:
        self._session = session
        self._table_name = table_name

    async def save(self, item: dict[str, Any]) -> None:
        async with self._session.resource("dynamodb") as dynamo:
            table = await dynamo.Table(self._table_name)
            await table.put_item(Item=item)

    async def find_by_id(self, order_id: str, item_id: str) -> dict[str, Any] | None:
        async with self._session.resource("dynamodb") as dynamo:
            table = await dynamo.Table(self._table_name)
            response = await table.get_item(
                Key={"orderId": order_id, "itemId": item_id}
            )
            return response.get("Item")

    async def delete(self, order_id: str, item_id: str) -> None:
        async with self._session.resource("dynamodb") as dynamo:
            table = await dynamo.Table(self._table_name)
            await table.delete_item(Key={"orderId": order_id, "itemId": item_id})
```

> Use `Decimal` para números armazenados no DynamoDB — boto3 converte `float` com perda de precisão.

---

## Secrets Manager

```python
import json
from typing import TypeVar
from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)

class SecretsService:
    def __init__(self, session: aioboto3.Session) -> None:
        self._session = session

    async def get_secret(self, secret_id: str) -> str:
        async with self._session.client("secretsmanager") as sm:
            response = await sm.get_secret_value(SecretId=secret_id)
            if "SecretString" not in response:
                raise ValueError(f"Secret {secret_id} is binary — not supported")
            return response["SecretString"]

    async def get_secret_as(self, secret_id: str, model: type[T]) -> T:
        raw = await self.get_secret(secret_id)
        return model.model_validate_json(raw)

# Exemplo
class DbCredentials(BaseModel):
    url: str
    username: str
    password: str

creds = await secrets_service.get_secret_as("/myapp/prod/db", DbCredentials)
```

---

## EventBridge

```python
import json
from datetime import datetime, UTC

class EventBridgeService:
    def __init__(self, session: aioboto3.Session, event_bus_name: str) -> None:
        self._session = session
        self._event_bus_name = event_bus_name

    async def publish(
        self,
        source: str,
        detail_type: str,
        detail: dict,
    ) -> None:
        async with self._session.client("events") as events:
            await events.put_events(
                Entries=[{
                    "EventBusName": self._event_bus_name,
                    "Source": source,
                    "DetailType": detail_type,
                    "Detail": json.dumps(detail),
                    "Time": datetime.now(UTC),
                }]
            )
```

---

## Testes unitários com moto (sem infra real)

```python
# tests/test_storage.py
import boto3
import pytest
from moto import mock_aws

@pytest.fixture
def s3_client():
    with mock_aws():
        client = boto3.client("s3", region_name="us-east-1")
        client.create_bucket(Bucket="test-bucket")
        yield client

@mock_aws
def test_upload_and_download(s3_client):
    s3_client.put_object(Bucket="test-bucket", Key="file.txt", Body=b"hello")
    response = s3_client.get_object(Bucket="test-bucket", Key="file.txt")
    assert response["Body"].read() == b"hello"

# Para async (aioboto3) use pytest-asyncio + moto
@pytest.mark.asyncio
@mock_aws
async def test_async_upload():
    import aioboto3
    session = aioboto3.Session()
    async with session.client("s3", region_name="us-east-1") as s3:
        await s3.create_bucket(Bucket="test-bucket")
        await s3.put_object(Bucket="test-bucket", Key="file.txt", Body=b"world")
        resp = await s3.get_object(Bucket="test-bucket", Key="file.txt")
        assert await resp["Body"].read() == b"world"
```

## Testes de integração com Testcontainers + LocalStack

```python
# tests/conftest.py
import pytest
from testcontainers.localstack import LocalStackContainer

@pytest.fixture(scope="session")
def localstack():
    with LocalStackContainer(image="localstack/localstack:3") as ls:
        yield ls

@pytest.fixture
def s3_localstack(localstack):
    import boto3
    client = boto3.client(
        "s3",
        endpoint_url=localstack.get_url(),
        aws_access_key_id="test",
        aws_secret_access_key="test",
        region_name="us-east-1",
    )
    client.create_bucket(Bucket="test-bucket")
    yield client
```

---

## Regras de segurança

- Nunca passe `aws_access_key_id` / `aws_secret_access_key` hardcoded — use variáveis de ambiente ou IAM roles
- Nunca logue o retorno de `get_secret_value` — logue apenas o `SecretId`
- Use `moto` para testes unitários (sem rede) e LocalStack para integração
- Use `aioboto3` em `async def` — nunca chame `boto3` bloqueante dentro de um event loop asyncio
- Para operações de alto volume no DynamoDB, use `batch_write_item` e `batch_get_item` em vez de chamadas individuais
- Defina `region_name` explicitamente — não dependa do default `us-east-1` implícito
