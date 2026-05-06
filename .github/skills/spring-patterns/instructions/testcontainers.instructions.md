---
description: "Use when writing Spring Boot integration tests with Testcontainers. Covers @ServiceConnection (Boot 3.1+), @DynamicPropertySource, @SpringBootTest, reusable base class, Kafka, LocalStack, @DataJpaTest with real DB, and Awaitility."
---

# Testcontainers com Spring Boot

> Para os fundamentos (containers, wait strategies, GenericContainer, redes) veja:
> [testcontainers.instructions.md](../../java-patterns/instructions/testcontainers.instructions.md)

## Dependências

```xml
<!-- Spring Boot 3.x — inclui integração @ServiceConnection -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

## `@ServiceConnection` — Spring Boot 3.1+ (zero configuração)

Registra automaticamente as propriedades do container no contexto Spring — sem `@DynamicPropertySource`.

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    OrderService service;

    @Test
    void shouldPersistAndPublishEvent() {
        var order = service.create(new CreateOrderRequest(UUID.randomUUID(), 2));
        assertThat(order.id()).isNotNull();
    }
}
```

**Suporte nativo** ao `@ServiceConnection` por tipo de container:

| Container | Configura automaticamente |
|-----------|--------------------------|
| `PostgreSQLContainer` | `spring.datasource.*` |
| `MySQLContainer` | `spring.datasource.*` |
| `RedisContainer` | `spring.data.redis.*` |
| `KafkaContainer` | `spring.kafka.bootstrap-servers` |
| `RabbitMQContainer` | `spring.rabbitmq.*` |
| `MongoDBContainer` | `spring.data.mongodb.*` |

---

## `@DynamicPropertySource` — configuração manual

Use quando `@ServiceConnection` não suporta o container ou precisa de propriedades customizadas.

```java
@SpringBootTest
@Testcontainers
class ReportServiceTest {

    @Container
    static LocalStackContainer localStack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
                    .withServices(LocalStackContainer.Service.S3,
                                  LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void configureLocalStack(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localStack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("spring.cloud.aws.sqs.endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
    }
}
```

---

## Classe base — containers compartilhados entre classes de teste

Evita re-start do container a cada classe; o Spring re-usa o contexto quando as propriedades são idênticas.

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
abstract class IntegrationTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withReuse(true);

    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                    .withReuse(true);
}

// Subclasse herda os containers e o contexto Spring
class OrderIntegrationTest extends IntegrationTestBase {

    @Autowired
    OrderService service;

    @Test
    @Transactional
    void shouldCreateOrder() { ... }
}
```

---

## `@DataJpaTest` com banco real (sem H2)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

        assertThat(orders).hasSize(1);
    }
}
```

---

## Kafka — producer + consumer

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
class OrderEventTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Autowired
    OrderEventConsumer consumer;

    @Test
    void shouldConsumeOrderCreatedEvent() {
        kafkaTemplate.send("order.created",
                new OrderCreatedEvent(UUID.randomUUID(), "PENDING"));

        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() ->
                   assertThat(consumer.getProcessedCount()).isGreaterThan(0));
    }
}
```

---

## LocalStack — AWS services locais

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
class S3StorageServiceTest {

    @Container
    static LocalStackContainer localStack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
                    .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static", localStack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localStack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    }

    @Autowired
    S3StorageService storageService;

    @Test
    void shouldUploadAndDownloadFile() {
        storageService.upload("my-bucket", "key.txt", "content".getBytes());
        var content = storageService.download("my-bucket", "key.txt");
        assertThat(new String(content)).isEqualTo("content");
    }
}
```

---

## Estrutura recomendada

```
src/test/
├── java/com/example/
│   ├── unit/                        # @ExtendWith(MockitoExtension) — sem container
│   ├── slice/                       # @WebMvcTest, @DataJpaTest
│   │   └── OrderRepositoryTest.java # @DataJpaTest + @ServiceConnection (real DB)
│   └── integration/                 # @SpringBootTest + Testcontainers
│       ├── IntegrationTestBase.java # containers compartilhados
│       ├── OrderIntegrationTest.java
│       └── S3StorageServiceTest.java
└── resources/
    └── application-integration.properties
```

---

## Separar testes de integração na build Maven

```xml
<!-- pom.xml — executa @Tag("integration") somente com -P integration -->
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

- Prefira `@ServiceConnection` a `@DynamicPropertySource` — menos código e mais seguro
- Use `Replace.NONE` em `@DataJpaTest` quando o código usa SQL nativo ou features específicas do banco
- Declare containers `static` na classe base — o Spring re-usa o contexto quando as propriedades são iguais
- Use `Awaitility` para assertions assíncronas (Kafka, SQS) — nunca `Thread.sleep()`
- Use `.withReuse(true)` localmente; desabilite no CI com `testcontainers.reuse.enable=false`
