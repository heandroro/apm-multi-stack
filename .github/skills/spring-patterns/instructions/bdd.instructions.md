---
description: "Use when writing BDD tests in Spring Boot applications. Covers cucumber-spring, @CucumberContextConfiguration, @SpringBootTest + Testcontainers, @MockBean in steps, hooks com @Autowired, e RestAssured com @LocalServerPort."
---

# BDD com Cucumber — Integração Spring Boot

> Para fundamentos do Cucumber (Gherkin, step definitions, Data Tables, Scenario Outline, tags, hooks) veja:
> [bdd.instructions.md](../../java-patterns/instructions/bdd.instructions.md)

## Dependência adicional

```xml
<!-- Além das dependências core do Cucumber -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-spring</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>
```

---

## `@CucumberContextConfiguration` — contexto Spring compartilhado

Uma única classe no pacote de steps anotada com `@CucumberContextConfiguration` inicializa o contexto Spring. Todos os step definitions do mesmo `glue` compartilham esse contexto.

```java
// src/test/java/com/example/steps/SpringIntegrationConfig.java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class SpringIntegrationConfig {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
```

> Apenas **uma** classe por glue pode ter `@CucumberContextConfiguration`. Coloque-a em uma classe dedicada — não misture com step definitions.

---

## Steps como `@Component`

Com `cucumber-spring`, os step definitions são gerenciados pelo Spring — use `@Autowired`, `@MockBean` e `@LocalServerPort` normalmente.

```java
@Component
public class OrderSteps {

    @LocalServerPort
    private int port;

    @MockBean                          // substitui bean real no contexto Spring
    private PaymentGateway paymentGateway;

    private RequestSpecification request;
    private Response response;
    private UUID customerId;

    @Before
    public void setUp() {
        request = RestAssured.given()
                .baseUri("http://localhost:" + port)
                .contentType(ContentType.JSON);
    }

    @Given("a registered customer with id {string}")
    public void aRegisteredCustomerWithId(String id) {
        customerId = UUID.fromString(id);
    }

    @When("I place an order for product {string} with quantity {int}")
    public void iPlaceAnOrder(String productCode, int quantity) {
        response = request
                .body(Map.of("customerId", customerId,
                             "productCode", productCode,
                             "quantity", quantity))
                .post("/api/v1/orders");
    }

    @Then("the order should be created with status {string}")
    public void theOrderShouldBeCreatedWithStatus(String status) {
        response.then()
                .statusCode(201)
                .body("status", equalTo(status));
    }

    @Then("the request should fail with status {int}")
    public void theRequestShouldFailWithStatus(int statusCode) {
        response.then().statusCode(statusCode);
    }
}
```

---

## Hooks com `@Autowired`

```java
@Component
public class DatabaseCleanupHooks {

    @Autowired
    private OrderRepository orderRepository;

    @Before("@Order")
    public void cleanOrders() {
        orderRepository.deleteAll();
    }

    @After
    public void logResult(Scenario scenario) {
        if (scenario.isFailed()) {
            // capturar detalhes para debug
        }
    }
}
```

---

## Mockar beans Spring nos steps

```java
@Component
public class PaymentSteps {

    @MockBean
    private PaymentGateway paymentGateway;

    @Given("the payment gateway is unavailable")
    public void thePaymentGatewayIsUnavailable() {
        when(paymentGateway.charge(any()))
                .thenThrow(new GatewayException("timeout"));
    }
}
```

> `@MockBean` reinicia o contexto Spring se declarado em uma classe diferente da `@CucumberContextConfiguration`. Para evitar re-start, declare todos os `@MockBean` na classe de configuração ou em uma única classe de steps de contexto.

---

## Estrutura com Spring Boot

```
src/test/
├── java/com/example/
│   ├── CucumberSuite.java              # @Suite — entry point
│   ├── steps/
│   │   ├── SpringIntegrationConfig.java  # @CucumberContextConfiguration
│   │   ├── OrderSteps.java               # @Component
│   │   ├── PaymentSteps.java             # @Component + @MockBean
│   │   └── DatabaseCleanupHooks.java     # @Component + @Before/@After
└── resources/
    ├── junit-platform.properties
    └── features/
        └── order/
            └── create-order.feature
```

---

## Regras Spring-specific

- Apenas **uma** `@CucumberContextConfiguration` por suite — coloque em classe dedicada
- `@MockBean` na mesma classe que `@CucumberContextConfiguration` evita re-inicialização do contexto
- Use `RANDOM_PORT` em `@SpringBootTest` + `@LocalServerPort` para testar via HTTP real com RestAssured
- Combine `@ServiceConnection` na `SpringIntegrationConfig` — o container é compartilhado por todos os cenários
