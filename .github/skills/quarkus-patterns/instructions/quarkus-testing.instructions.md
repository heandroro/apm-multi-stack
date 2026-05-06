---
description: "Use when writing tests for Quarkus applications. Covers @QuarkusTest, @QuarkusIntegrationTest, Dev Services, @InjectMock, RestAssured, Panache mocking, and test profiles."
---

# Quarkus Testing — Boas Práticas

> **Testcontainers vs Dev Services**: Quarkus usa **Dev Services** — detecta automaticamente as extensões instaladas (PostgreSQL, Kafka, Redis…) e sobe os containers sem nenhum código de teste. Não é necessário declarar `@Container` ou `@ServiceConnection`. Para fundamentos do Testcontainers (GenericContainer, wait strategies, redes) veja [testcontainers.instructions.md](../../java-patterns/instructions/testcontainers.instructions.md).

## Dependências

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5-mockito</artifactId>
    <scope>test</scope>
</dependency>
```

---

## `@QuarkusTest` — Testes de integração com contexto completo

```java
@QuarkusTest
class OrderResourceTest {

    @Test
    void shouldCreateOrder() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    {"customerId": "550e8400-e29b-41d4-a716-446655440000", "quantity": 2}
                    """)
        .when()
            .post("/api/v1/orders")
        .then()
            .statusCode(201)
            .body("status", equalTo("PENDING"))
            .body("id", notNullValue());
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        given()
            .pathParam("id", UUID.randomUUID())
        .when()
            .get("/api/v1/orders/{id}")
        .then()
            .statusCode(404)
            .body("code", equalTo("ORDER_NOT_FOUND"));
    }
}
```

---

## Dev Services — banco de dados automático em testes

Quarkus sobe automaticamente um container PostgreSQL (ou outro) via Testcontainers quando não há datasource configurado para o perfil de teste.

```properties
# src/test/resources/application.properties — sem datasource = Dev Services ativo
# Quarkus detecta a extensão jdbc-postgresql e sobe o container automaticamente

# Para desabilitar Dev Services (ex: CI com banco dedicado)
quarkus.datasource.devservices.enabled=false
quarkus.datasource.jdbc.url=${TEST_DB_URL}
```

```properties
# Customizar a imagem Docker do Dev Services
quarkus.datasource.devservices.image-name=postgres:16-alpine
```

**Nenhuma configuração adicional é necessária** para usar Dev Services — é o padrão quando a extensão está no classpath e não há URL configurada.

---

## `@InjectMock` — Mockar beans CDI

```java
@QuarkusTest
class OrderServiceTest {

    @InjectMock
    PaymentGateway paymentGateway;   // substitui o bean CDI real

    @Inject
    OrderService orderService;       // recebe o mock injetado

    @Test
    void shouldFailWhenPaymentDeclined() {
        when(paymentGateway.charge(any())).thenThrow(new PaymentDeclinedException("declined"));

        assertThatThrownBy(() -> orderService.pay(UUID.randomUUID(), new BigDecimal("100")))
                .isInstanceOf(PaymentFailedException.class);
    }
}
```

---

## `@TestHTTPEndpoint` — Simplificar URL nos testes

```java
@QuarkusTest
@TestHTTPEndpoint(OrderResource.class)   // usa o @Path da classe automaticamente
class OrderResourceTest {

    @Test
    void shouldListOrders() {
        given()
        .when()
            .get()          // GET /api/v1/orders — path resolvido pelo @TestHTTPEndpoint
        .then()
            .statusCode(200);
    }
}
```

---

## Test Profiles — configuração por perfil de teste

```java
// Definir profile
public class MockExternalServicesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "app.payment.gateway-url", "http://localhost:8089",
            "app.feature.new-checkout", "true"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test-mock";
    }
}

// Usar no teste
@QuarkusTest
@TestProfile(MockExternalServicesProfile.class)
class CheckoutFlowTest { ... }
```

---

## Panache — mockar queries estáticas

```java
@QuarkusTest
class OrderServicePanacheTest {

    @InjectMock
    OrderRepository repository;

    @Inject
    OrderService service;

    @Test
    void shouldReturnOrderWhenFound() {
        var order = new Order();
        order.id = UUID.randomUUID();
        order.status = OrderStatus.PENDING;

        when(repository.findByIdOptional(order.id))
                .thenReturn(Optional.of(order));

        var result = service.findById(order.id).await().indefinitely();

        assertThat(result.status()).isEqualTo("PENDING");
    }
}
```

Para Active Record (sem repository), use `PanacheMock`:

```java
@QuarkusTest
class OrderActiveRecordTest {

    @Test
    @TestTransaction   // transação revertida ao fim do teste
    void shouldPersistOrder() {
        var order = new Order();
        order.customerId = UUID.randomUUID();
        order.status = OrderStatus.PENDING;
        order.persist();

        assertThat(Order.count()).isEqualTo(1);
    }
}
```

---

## `@QuarkusIntegrationTest` — Teste contra binário nativo ou JVM jar

```java
// Roda exatamente o mesmo teste, mas contra o artefato compilado (jar ou native)
// Execute com: ./mvnw verify -Pnative
@QuarkusIntegrationTest   // substitui @QuarkusTest — sem mudanças no corpo
class OrderResourceIT extends OrderResourceTest {}
```

---

## `@TestTransaction` — rollback automático

```java
@QuarkusTest
class OrderRepositoryTest {

    @Inject
    OrderRepository repository;

    @Test
    @TestTransaction   // tudo dentro do teste é revertido ao terminar
    void shouldSaveAndFind() {
        var order = new Order();
        order.customerId = UUID.randomUUID();
        order.status = OrderStatus.PENDING;
        repository.persist(order);
        repository.flush();

        var found = repository.findByIdOptional(order.id);
        assertThat(found).isPresent();
    }
}
```

---

## Estrutura recomendada

```
src/test/
├── java/com/example/
│   ├── resource/           # @QuarkusTest — testes de endpoint via RestAssured
│   │   └── OrderResourceTest.java
│   ├── service/            # @QuarkusTest + @InjectMock — testes de serviço
│   │   └── OrderServiceTest.java
│   ├── repository/         # @QuarkusTest + @TestTransaction — testes de repositório
│   │   └── OrderRepositoryTest.java
│   └── it/                 # @QuarkusIntegrationTest — roda com -Pnative
│       └── OrderResourceIT.java
└── resources/
    └── application.properties   # overrides para o perfil 'test'
```

---

## Regras

- Prefira Dev Services a configurar datasources de teste manualmente
- Use `@TestTransaction` em testes de repositório para isolar estado do banco
- Use `@InjectMock` (não `@MockBean` do Spring) para substituir beans CDI
- `@QuarkusIntegrationTest` em pelo menos um smoke test por serviço — garante que o binário nativo funciona
- Nunca chame `.await().indefinitely()` em produção — somente em testes `@QuarkusTest`
