---
description: "Use when writing or reviewing Java tests. Covers JUnit 5 (Jupiter) with Mockito, AssertJ, @Nested, and parameterized tests. Framework-agnostic — applies to all Java versions (11–25). For Spring Boot slices (@WebMvcTest, @DataJpaTest, @SpringBootTest) see spring-patterns."
---

# JUnit 5 — Boas Práticas de Testes Java

> Para uso avançado veja também:
> - **Spring Boot slices** (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`, `@MockBean`) → [testing.instructions.md](../../spring-patterns/instructions/testing.instructions.md)
> - **Testcontainers** → [testcontainers.instructions.md](./testcontainers.instructions.md)
> - **BDD** → [bdd.instructions.md](./bdd.instructions.md)

## Dependências (Maven)

```xml
<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Mockito -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

```xml
<!-- Maven Surefire — habilitar JUnit Platform -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

---

## Anatomia de um teste JUnit 5

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private OrderService service;

    @BeforeEach
    void setUp() {
        // inicialização comum antes de cada teste — opcional com @ExtendWith
    }

    @Test
    @DisplayName("deve criar pedido quando produto está em estoque")
    void shouldCreateOrderWhenProductInStock() {
        // Arrange
        var productId = UUID.randomUUID();
        when(repository.findProduct(productId))
                .thenReturn(Optional.of(new Product(productId, 10)));

        // Act
        var order = service.createOrder(productId, 2);

        // Assert
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(repository).save(any(Order.class));
    }
}
```

**Convenções de nomenclatura:**
- Nome da classe: `${ClasseTestada}Test` (ex: `OrderServiceTest`)
- Nome do método: `should<Resultado>When<Condição>` ou `deve<Resultado>Quando<Condição>`
- Use `@DisplayName` para legibilidade nos relatórios

---

## Anotações essenciais do JUnit 5

| Anotação | Uso |
|----------|-----|
| `@Test` | Método de teste |
| `@DisplayName` | Nome legível do teste nos relatórios |
| `@BeforeEach` / `@AfterEach` | Setup/teardown por teste |
| `@BeforeAll` / `@AfterAll` | Setup/teardown por classe (métodos `static`) |
| `@Nested` | Agrupa testes relacionados — melhora leitura |
| `@Tag("integration")` | Categoriza testes para execução seletiva |
| `@Disabled("motivo")` | Desabilita teste com justificativa obrigatória |
| `@Timeout(5)` | Falha se o teste ultrapassar N segundos |
| `@ExtendWith` | Registra extension (ex: `MockitoExtension`) |

---

## Assertions com AssertJ

Prefira **AssertJ** a `Assertions.assertEquals` do JUnit — mensagens de erro são muito mais legíveis.

```java
// Valores simples
assertThat(result).isEqualTo(42);
assertThat(name).isEqualTo("Alice").startsWith("A").hasSize(5);
assertThat(value).isNull();
assertThat(optional).isPresent().hasValue("expected");

// Coleções
assertThat(list).hasSize(3)
                .containsExactly("a", "b", "c")
                .doesNotContain("d");

assertThat(map).containsKey("id")
               .containsEntry("status", "ACTIVE");

// Objetos — campo a campo (sem precisar de equals)
assertThat(order)
    .extracting(Order::getStatus, Order::getTotal)
    .containsExactly(OrderStatus.PENDING, new BigDecimal("100.00"));

// Exceções
assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
    .isInstanceOf(OrderNotFoundException.class)
    .hasMessageContaining("not found");

assertThatExceptionOfType(OrderNotFoundException.class)
    .isThrownBy(() -> service.findById(UUID.randomUUID()))
    .withMessageContaining("not found");
```

---

## Mockito

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentGateway gateway;

    @InjectMocks
    PaymentService service;

    @Test
    void shouldChargeCard() {
        when(gateway.charge(any(ChargeRequest.class)))
            .thenReturn(new ChargeResult("txn-123", ChargeStatus.SUCCESS));

        var result = service.pay(new PaymentRequest("4111...", 100));

        assertThat(result.transactionId()).isEqualTo("txn-123");
    }

    @Test
    void shouldNotChargeWhenAmountIsZero() {
        service.pay(new PaymentRequest("4111...", 0));
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldSendCorrectAmount() {
        var captor = ArgumentCaptor.forClass(ChargeRequest.class);
        service.pay(new PaymentRequest("4111...", 150));
        verify(gateway).charge(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(150);
    }

    @Test
    void shouldThrowWhenGatewayFails() {
        when(gateway.charge(any())).thenThrow(new GatewayException("timeout"));
        assertThatThrownBy(() -> service.pay(new PaymentRequest("4111...", 50)))
            .isInstanceOf(PaymentFailedException.class);
    }
}
```

---

## `@Nested` — organização por cenário

```java
@DisplayName("OrderService")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("deve retornar pedido quando produto disponível")
        void shouldReturnOrderWhenProductAvailable() { ... }

        @Test
        @DisplayName("deve lançar exceção quando estoque insuficiente")
        void shouldThrowWhenInsufficientStock() { ... }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("deve cancelar quando pedido ainda não foi enviado")
        void shouldCancelWhenNotShipped() { ... }

        @Test
        @DisplayName("deve lançar exceção quando pedido já foi enviado")
        void shouldThrowWhenAlreadyShipped() { ... }
    }
}
```

---

## Testes parametrizados

```java
@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t"})
@DisplayName("deve rejeitar nomes em branco")
void shouldRejectBlankNames(String name) {
    assertThatThrownBy(() -> service.create(name))
        .isInstanceOf(IllegalArgumentException.class);
}

// CSV inline
@ParameterizedTest
@CsvSource({
    "100.00, 10, 90.00",
    "200.00, 50, 100.00",
    "50.00,   0, 50.00",
})
void shouldApplyDiscount(BigDecimal price, int discountPercent, BigDecimal expected) {
    assertThat(service.applyDiscount(price, discountPercent))
        .isEqualByComparingTo(expected);
}

// CSV de arquivo externo
@ParameterizedTest
@CsvFileSource(resources = "/test-data/discount-cases.csv", numLinesToSkip = 1)
void shouldApplyDiscountFromFile(BigDecimal price, int pct, BigDecimal expected) { ... }

// Método factory — objetos complexos
@ParameterizedTest
@MethodSource("invalidOrders")
void shouldRejectInvalidOrder(Order order, String expectedMessage) {
    assertThatThrownBy(() -> service.validate(order))
        .hasMessageContaining(expectedMessage);
}

static Stream<Arguments> invalidOrders() {
    return Stream.of(
        Arguments.of(new Order(null, 1), "customerId is required"),
        Arguments.of(new Order(UUID.randomUUID(), 0), "quantity must be positive")
    );
}
```

---

## Estrutura de diretórios recomendada

```
src/test/
├── java/com/example/
│   ├── unit/               # @ExtendWith(MockitoExtension) — sem contexto de framework
│   │   ├── OrderServiceTest.java
│   │   └── PaymentServiceTest.java
│   └── integration/        # @Tag("integration") + Testcontainers
│       └── OrderIntegrationTest.java
└── resources/
    ├── application-test.properties
    └── test-data/
        └── discount-cases.csv
```

> Spring Boot: adicionar `slice/` com `@WebMvcTest` e `@DataJpaTest` → [testing.instructions.md](../../spring-patterns/instructions/testing.instructions.md)

---

## Separar testes de integração no Maven

```xml
<profiles>
    <profile>
        <id>integration</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <groups>integration</groups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

---

## Regras

- Testes unitários sem contexto de framework — `@ExtendWith(MockitoExtension.class)` — são 100x mais rápidos que `@SpringBootTest`
- Nunca use `Thread.sleep()` — use `Awaitility` para assertions assíncronas
- Mantenha cada teste independente — sem dependência de ordem de execução
- Um conceito por teste (pode ter vários `assertThat` se testam a mesma coisa)
- `@Tag("integration")` em testes que sobem containers — execute separado da build rápida
