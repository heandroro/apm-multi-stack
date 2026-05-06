---
description: "Use when writing Spring Boot tests. Covers @WebMvcTest (MockMvc), @DataJpaTest, @SpringBootTest, @MockBean, @RestClientTest, slice test patterns, and integration with Testcontainers."
---

# Spring Boot Testing — Slices e Integração

> Para fundamentos de JUnit 5, Mockito e AssertJ (framework-agnostic) veja:
> [junit.instructions.md](../../java-patterns/instructions/junit.instructions.md)
>
> Para Testcontainers com Spring Boot veja:
> [testcontainers.instructions.md](./testcontainers.instructions.md)

## Dependência

```xml
<!-- Inclui: JUnit Jupiter, Mockito, AssertJ, Hamcrest, JSONPath -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## `@WebMvcTest` — slice de Controller

Sobe apenas a camada web (controllers, filters, `@ControllerAdvice`). Não sobe `@Service` nem `@Repository` — use `@MockBean`.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService orderService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void shouldReturn201WhenOrderCreated() throws Exception {
        var request = new CreateOrderRequest(UUID.randomUUID(), 2);
        var response = new OrderResponse(UUID.randomUUID(), OrderStatus.PENDING);
        when(orderService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.findById(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/v1/orders/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
```

---

## `@DataJpaTest` — slice de Repository

Sobe apenas JPA (entidades, repositories, `TestEntityManager`). Não sobe controllers nem services.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Replace.NONE = usar banco real via Testcontainers em vez de H2
@Testcontainers
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    OrderRepository repository;

    @Autowired
    TestEntityManager em;

    @Test
    void shouldFindByCustomerId() {
        var customerId = UUID.randomUUID();
        em.persist(new Order(customerId, 2, OrderStatus.PENDING));
        em.flush();

        var orders = repository.findByCustomerId(customerId);

        assertThat(orders).hasSize(1)
                          .extracting(Order::getCustomerId)
                          .containsOnly(customerId);
    }

    @Test
    void shouldReturnEmptyWhenNoOrders() {
        assertThat(repository.findByCustomerId(UUID.randomUUID())).isEmpty();
    }
}
```

---

## `@RestClientTest` — slice de HTTP client

Testa classes anotadas com `@Component` que usam `RestClient`, `RestTemplate` ou `@HttpExchange`.

```java
@RestClientTest(InventoryClient.class)
class InventoryClientTest {

    @Autowired
    InventoryClient client;

    @Autowired
    MockRestServiceServer server;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void shouldReturnStockLevel() throws Exception {
        var productId = UUID.randomUUID();
        var expected = new StockResponse(productId, 42);

        server.expect(requestTo("/api/inventory/" + productId))
              .andRespond(withSuccess(
                  objectMapper.writeValueAsString(expected),
                  MediaType.APPLICATION_JSON));

        var result = client.getStock(productId);

        assertThat(result.quantity()).isEqualTo(42);
    }
}
```

---

## `@SpringBootTest` — teste de integração completo

Sobe o contexto Spring completo. Combine com Testcontainers para infra real.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @LocalServerPort
    private int port;

    @Autowired
    OrderService service;

    private RequestSpecification request;

    @BeforeEach
    void setUp() {
        request = RestAssured.given()
                .baseUri("http://localhost:" + port)
                .contentType(ContentType.JSON);
    }

    @Test
    void shouldCreateAndRetrieveOrder() {
        var body = Map.of("customerId", UUID.randomUUID(), "quantity", 2);

        var id = request.body(body)
                        .post("/api/v1/orders")
                        .then()
                        .statusCode(201)
                        .extract().jsonPath().getUUID("id");

        request.get("/api/v1/orders/" + id)
               .then()
               .statusCode(200)
               .body("status", equalTo("PENDING"));
    }
}
```

---

## `@MockBean` — substituir bean no contexto Spring

```java
@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PaymentGateway paymentGateway;   // substitui o bean real no contexto

    @MockBean
    OrderService orderService;

    @Test
    void shouldReturn402WhenPaymentDeclined() throws Exception {
        when(paymentGateway.charge(any())).thenThrow(new PaymentDeclinedException());

        mockMvc.perform(post("/api/v1/checkout").contentType(APPLICATION_JSON).content("..."))
               .andExpect(status().isPaymentRequired());
    }
}
```

> `@MockBean` reinicia o contexto Spring — declare todos os mocks necessários de uma só vez para evitar re-starts desnecessários.

---

## Estrutura recomendada

```
src/test/
├── java/com/example/
│   ├── unit/               # @ExtendWith(MockitoExtension) — sem Spring
│   │   └── OrderServiceTest.java
│   ├── slice/              # contexto parcial — rápido
│   │   ├── OrderControllerTest.java    # @WebMvcTest
│   │   ├── OrderRepositoryTest.java    # @DataJpaTest + @ServiceConnection
│   │   └── InventoryClientTest.java    # @RestClientTest
│   └── integration/        # @SpringBootTest + Testcontainers
│       ├── IntegrationTestBase.java    # containers estáticos compartilhados
│       └── OrderIntegrationTest.java
└── resources/
    └── application-test.properties
```

---

## Tabela de decisão

| Cenário | Anotação | Velocidade |
|---------|----------|-----------|
| Testar lógica de service/domain | `@ExtendWith(MockitoExtension)` | ⚡ mais rápido |
| Testar controller (rotas, status, JSON) | `@WebMvcTest` | ⚡ rápido |
| Testar queries JPA / repositório | `@DataJpaTest` + `@ServiceConnection` | 🔶 médio |
| Testar HTTP client | `@RestClientTest` | ⚡ rápido |
| Teste de ponta a ponta | `@SpringBootTest` + Testcontainers | 🐢 lento |

---

## Regras

- Prefira slices (`@WebMvcTest`, `@DataJpaTest`) a `@SpringBootTest` sempre que possível
- Nunca use H2 in-memory para `@DataJpaTest` — use `Replace.NONE` + Testcontainers
- Declare todos os `@MockBean` que o teste precisa em uma única classe — evita re-start de contexto
- `@SpringBootTest` pertence à camada de integração com `@Tag("integration")` — rode separado no CI
