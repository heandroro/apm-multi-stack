---
name: define-proto
description: "Define a .proto file and generate Go gRPC service skeleton from a natural language description of a service."
---

Defina um arquivo `.proto` e gere o esqueleto da implementação Go para o seguinte serviço:

**Nome do serviço**: $service_name (ex: UserService, PaymentService)
**Domínio / recurso**: $resource (ex: User, Payment, Order)
**Operações**:
- RPCs unárias: $unary_rpcs (ex: Create, Get, Update, Delete)
- RPCs com streaming: $streaming_rpcs (ex: ListAll como server-streaming, Watch como bidirectional)
**Módulo Go**: $module (ex: github.com/myorg/myservice)
**Package proto**: $proto_package (ex: user.v1, payment.v1)

## O que gerar

### 1. Arquivo `.proto`

Seguindo as convenções em [grpc-protobuf.instructions.md](../instructions/grpc-protobuf.instructions.md):
- `syntax = "proto3"`
- `option go_package` correto
- Mensagens de request/response por RPC (nunca reutilize mensagens entre RPCs)
- Use `google.protobuf.Timestamp` para datas, `google.protobuf.Empty` para respostas vazias
- Nomes de campos em `snake_case`, mensagens em `PascalCase`

### 2. Interface Go do service

```go
type ${service_name} interface {
    // uma função por RPC
}
```

### 3. Struct do servidor gRPC

- Embed `Unimplemented${service_name}Server`
- Um método por RPC com mapeamento de erros via `toGRPCStatus`

### 4. Comando de geração

```bash
# buf
buf generate
```

## Regras

- Siga a convenção de versionamento de packages: `{domain}.v1`
- Nunca reutilize tipos de request entre RPCs diferentes
- Sempre use `codes.Internal` para erros não mapeados (nunca vaze detalhes internos)
