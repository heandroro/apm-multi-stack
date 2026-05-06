---
description: "Use when working with SmallRye extensions in Quarkus. Covers SmallRye Fault Tolerance (@Retry, @CircuitBreaker, @Fallback, @Timeout, @Bulkhead), SmallRye Health, SmallRye OpenAPI, and SmallRye Config."
---

# SmallRye Extensions — Quarkus

## SmallRye Fault Tolerance

### Dependência

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
</dependency>
```

### `@Retry`

```java
@ApplicationScoped
public class PaymentGatewayClient {

    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
           retryOn = { IOException.class, TimeoutException.class })
    public Uni<ChargeResult> charge(ChargeRequest request) {
        return callExternalGateway(request);
    }
}
```

### `@Timeout`

```java
@Timeout(value = 5, unit = ChronoUnit.SECONDS)
public Uni<InventoryResponse> checkStock(UUID productId) {
    return inventoryService.check(productId);
}
```

### `@CircuitBreaker`

```java
@CircuitBreaker(
    requestVolumeThreshold = 10,    // janela mínima de requisições
    failureRatio = 0.5,             // 50% de falhas abre o circuito
    delay = 30,                     // espera 30s antes de tentar fechar
    delayUnit = ChronoUnit.SECONDS,
    successThreshold = 2            // 2 sucessos para fechar
)
public Uni<PricingResponse> getPrice(UUID productId) {
    return pricingService.fetchPrice(productId);
}
```

### `@Fallback`

```java
@Fallback(fallbackMethod = "fallbackPrice")
@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 20,
                delayUnit = ChronoUnit.SECONDS)
public Uni<PricingResponse> getPrice(UUID productId) {
    return pricingService.fetchPrice(productId);
}

// Assinatura idêntica ao método principal
Uni<PricingResponse> fallbackPrice(UUID productId) {
    // retornar valor padrão, cache ou erro tratado
    return Uni.createFrom().item(PricingResponse.defaultPrice());
}
```

### `@Bulkhead` — limitar concorrência

```java
@Bulkhead(value = 10,          // máximo 10 execuções simultâneas
          waitingTaskQueue = 5) // fila de espera de até 5
public Uni<ReportResponse> generateReport(ReportRequest request) {
    return reportService.generate(request);
}
```

### Combinar anotações

```java
// Ordem de aplicação: Bulkhead → CircuitBreaker → Retry → Timeout → Fallback
@Retry(maxRetries = 2)
@Timeout(value = 3, unit = ChronoUnit.SECONDS)
@Fallback(fallbackMethod = "cachedResult")
public Uni<DataResponse> fetchData(String key) {
    return externalApi.getData(key);
}
```

---

## SmallRye Health

### Dependência

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

### Endpoints automáticos

| Endpoint | Descrição |
|----------|-----------|
| `GET /q/health` | Resultado combinado (liveness + readiness) |
| `GET /q/health/live` | Liveness — aplicação está rodando? |
| `GET /q/health/ready` | Readiness — pronta para receber tráfego? |
| `GET /q/health/started` | Startup — inicialização concluída? |

Datasources, mensageria e outros recursos têm health checks registrados automaticamente pelas extensões Quarkus.

### Health check customizado

```java
@Readiness   // ou @Liveness, @Startup
@ApplicationScoped
public class ExternalApiHealthCheck implements HealthCheck {

    @Inject
    ExternalApiClient client;

    @Override
    public HealthCheckResponse call() {
        try {
            client.ping();
            return HealthCheckResponse.up("external-api");
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("external-api")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
```

---

## SmallRye OpenAPI

### Dependência

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-openapi</artifactId>
</dependency>
```

### Configuração

```properties
# application.properties
quarkus.smallrye-openapi.info-title=Order Service API
quarkus.smallrye-openapi.info-version=1.0.0
quarkus.smallrye-openapi.info-description=REST API for order management
quarkus.swagger-ui.always-include=true    # habilita Swagger UI em produção (desabilitado por padrão)
quarkus.swagger-ui.path=/swagger-ui
```

### Anotações no resource

```java
@Path("/api/v1/orders")
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderResource {

    @GET
    @Path("/{id}")
    @Operation(summary = "Find order by ID")
    @APIResponse(responseCode = "200", description = "Order found",
                 content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @APIResponse(responseCode = "404", description = "Order not found")
    public Uni<OrderResponse> findById(@PathParam("id") UUID id) { ... }
}
```

---

## SmallRye Config

### Dependência — já incluída em `quarkus-core`

### Configuração tipada com `@ConfigMapping`

```java
@ConfigMapping(prefix = "app.payment")
public interface PaymentConfig {

    String gatewayUrl();
    Duration timeout();
    int maxRetries();

    // Grupo aninhado
    RetryConfig retry();

    interface RetryConfig {
        int maxAttempts();
        Duration backoff();
    }
}

// application.properties
// app.payment.gateway-url=https://gateway.example.com
// app.payment.timeout=5s
// app.payment.max-retries=3
// app.payment.retry.max-attempts=3
// app.payment.retry.backoff=500ms
```

### Configuração por perfil

```properties
# application.properties — valor padrão
app.payment.gateway-url=https://gateway.example.com

# application-dev.properties — sobrescreve no perfil 'dev'
app.payment.gateway-url=http://localhost:9090

# application-test.properties — sobrescreve no perfil 'test'
app.payment.gateway-url=http://localhost:8089
```

### Ativar perfil

```bash
./mvnw quarkus:dev -Dquarkus.profile=staging
```

---

## Regras

- `@Retry` + `@CircuitBreaker` + `@Fallback` sempre em chamadas a serviços externos — nunca confie em timeout da JVM
- `@Fallback` deve retornar dados degradados mas funcionais — nunca re-lançar a exceção original
- Health checks `@Readiness` não devem chamar serviços externos de forma síncrona bloqueante
- Use `@ConfigMapping` para grupos de propriedades — mais seguro que `@ConfigProperty` individual em classes com muitas configs
- Nunca coloque segredos (passwords, tokens) em `application.properties` commitados — use variáveis de ambiente ou Vault
