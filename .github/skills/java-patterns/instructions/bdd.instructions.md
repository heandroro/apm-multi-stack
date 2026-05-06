---
description: "Use when writing BDD tests in Java. Covers Cucumber 7 with JUnit 5, Gherkin feature files, step definitions, tags, data tables, scenario outlines, and hooks. For Spring Boot integration (@CucumberContextConfiguration, @MockBean, @ServiceConnection) see spring-patterns."
---

# BDD com Cucumber — Boas Práticas Java

> Para integração com **Spring Boot** (`@CucumberContextConfiguration`, `@MockBean`, `@SpringBootTest`, Testcontainers) → [bdd.instructions.md](../../spring-patterns/instructions/bdd.instructions.md)

## Dependências

```xml
<!-- Cucumber com JUnit 5 -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>

<!-- Para descoberta automática de features pelo JUnit Platform -->
<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Configuração — Suite JUnit 5

```java
// src/test/java/com/example/CucumberSuite.java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")          // pasta com os .feature
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.steps")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
public class CucumberSuite {}
```

```properties
# src/test/resources/junit-platform.properties
cucumber.publish.quiet=true
cucumber.plugin=pretty, json:target/cucumber-reports/report.json, html:target/cucumber-reports/report.html
```

---

## Feature file — Gherkin

```gherkin
# src/test/resources/features/order/create-order.feature
@Order
Feature: Create Order
  As a customer
  I want to place an order
  So that I can receive the products I want

  Background:
    Given a registered customer with id "550e8400-e29b-41d4-a716-446655440000"

  Scenario: Successfully create an order
    When I place an order for product "PROD-001" with quantity 2
    Then the order should be created with status "PENDING"
    And the order total should be "199.98"

  Scenario: Reject order with insufficient stock
    Given the product "PROD-001" has only 1 unit in stock
    When I place an order for product "PROD-001" with quantity 5
    Then the request should fail with status 422
    And the error code should be "INSUFFICIENT_STOCK"

  @Ignore
  Scenario: Draft — needs product catalog integration
    Given ...
```

---

## Step definitions

```java
// src/test/java/com/example/steps/OrderSteps.java
public class OrderSteps {  // POJO simples — sem @Component (framework-agnostic)

    private RequestSpecification request;
    private Response response;
    private UUID customerId;

    @Before
    public void setUp() {
        // configure RestAssured, datasource, etc.
        request = RestAssured.given()
                .baseUri("http://localhost:8080")
                .contentType(ContentType.JSON);
    }

    @Given("a registered customer with id {string}")
    public void aRegisteredCustomerWithId(String id) {
        customerId = UUID.fromString(id);
    }

    @When("I place an order for product {string} with quantity {int}")
    public void iPlaceAnOrder(String productCode, int quantity) {
        response = request
                .body(Map.of("customerId", customerId, "productCode", productCode, "quantity", quantity))
                .post("/api/v1/orders");
    }

    @Then("the order should be created with status {string}")
    public void theOrderShouldBeCreatedWithStatus(String status) {
        response.then()
                .statusCode(201)
                .body("status", equalTo(status));
        orderId = UUID.fromString(response.jsonPath().getString("id"));
    }

    @Then("the order total should be {string}")
    public void theOrderTotalShouldBe(String total) {
        response.then()
                .body("total", equalTo(new BigDecimal(total)));
    }

    @Then("the request should fail with status {int}")
    public void theRequestShouldFailWithStatus(int statusCode) {
        response.then().statusCode(statusCode);
    }

    @Then("the error code should be {string}")
    public void theErrorCodeShouldBe(String code) {
        response.then().body("code", equalTo(code));
    }
}
```

---

## Data Tables — múltiplos registros

```gherkin
Scenario: Create order with multiple items
  When I place an order with the following items:
    | productCode | quantity | unitPrice |
    | PROD-001    | 2        | 99.99     |
    | PROD-002    | 1        | 49.99     |
  Then the order total should be "249.97"
```

```java
@When("I place an order with the following items:")
public void iPlaceAnOrderWithItems(DataTable dataTable) {
    var items = dataTable.asMaps().stream()
            .map(row -> Map.of(
                "productCode", row.get("productCode"),
                "quantity",    Integer.parseInt(row.get("quantity")),
                "unitPrice",   new BigDecimal(row.get("unitPrice"))
            ))
            .toList();

    response = request
            .body(Map.of("customerId", customerId, "items", items))
            .post("/api/v1/orders");
}
```

---

## Scenario Outline — exemplos parametrizados

```gherkin
Scenario Outline: Validate order quantity
  When I place an order for product "PROD-001" with quantity <quantity>
  Then the request should fail with status 400
  And the error message should contain "<message>"

  Examples:
    | quantity | message                    |
    | 0        | must be greater than zero  |
    | -1       | must be greater than zero  |
    | 1001     | exceeds maximum per order  |
```

---

## Tags — filtrar execução

```gherkin
@Smoke @Order
Feature: ...

  @Critical
  Scenario: ...

  @Slow @Integration
  Scenario: ...
```

```java
// Rodar apenas @Smoke
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@Smoke")

// Excluir @Slow
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @Slow")

// Combinar
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@Smoke and not @Slow")
```

---

## Hooks — setup e teardown por cenário

```java
public class DatabaseCleanupHooks {

    @Before("@Order")            // roda antes de cenários com @Order
    public void cleanOrders() {
        // limpar estado — ex: via JDBC diretamente ou API de reset
    }

    @After
    public void logResult(Scenario scenario) {
        if (scenario.isFailed()) {
            // capturar screenshot, logar detalhes, etc.
        }
    }
}
```

---

## Estrutura de diretórios

```
src/test/
├── java/com/example/
│   ├── CucumberSuite.java              # @Suite — entry point JUnit
│   └── steps/
│       ├── OrderSteps.java
│       ├── ProductSteps.java
│       └── shared/
│           └── CommonSteps.java        # steps compartilhados entre features
└── resources/
    ├── junit-platform.properties
    └── features/
        ├── order/
        │   ├── create-order.feature
        │   └── cancel-order.feature
        └── product/
            └── product-catalog.feature
```

> Spring Boot: adicionar `SpringIntegrationConfig.java` em `steps/` → veja [bdd.instructions.md](../../spring-patterns/instructions/bdd.instructions.md)

---

## Regras

- Um arquivo `.feature` por funcionalidade — não misture domínios no mesmo arquivo
- Steps reutilizáveis vão em `CommonSteps` — evite duplicar step definitions
- Cenários devem ser independentes — use `@Before` / `@After` para limpar estado
- Prefira Scenario Outline a testes parametrizados manuais — Gherkin já tem suporte nativo
- Não coloque lógica de negócio nos steps — os steps orquestram; a lógica fica no serviço
- Use `@Tag` para separar smoke, integration e slow tests — rode subconjuntos no CI
- Combine com Testcontainers via `@CucumberContextConfiguration` + `@ServiceConnection`