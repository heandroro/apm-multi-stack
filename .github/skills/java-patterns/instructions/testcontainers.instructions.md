---
description: "Use when writing Java integration tests with Testcontainers (framework-agnostic). Covers @Container, @Testcontainers, GenericContainer, wait strategies, reusable containers, network, and multi-container setup. For Spring Boot @ServiceConnection see spring-patterns. For Quarkus Dev Services see quarkus-testing.instructions.md."
---

# Testcontainers — Core (Framework-Agnostic)

> Integrações framework-específicas:
> - **Spring Boot** (`@ServiceConnection`, `@DynamicPropertySource`, `@SpringBootTest`) → [testcontainers.instructions.md](../../spring-patterns/instructions/testcontainers.instructions.md)
> - **Quarkus** — usa **Dev Services** (containers automáticos, sem código) → [quarkus-testing.instructions.md](../../quarkus-patterns/instructions/quarkus-testing.instructions.md)

## Dependências

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Módulos específicos — inclua apenas o que usar -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>localstack</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Container estático por classe

```java
@Testcontainers
class OrderRepositoryTest {

    @Container                   // static = iniciado uma vez, compartilhado entre testes da classe
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @BeforeAll
    static void setUp() {
        // postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
        // configurar datasource manualmente se não usar integração de framework
    }

    @Test
    void shouldPersist() { ... }
}
```

**Nunca declare `@Container` em campo de instância (não-static)** — um novo container é iniciado por teste, tornando os testes lentos.

---

## Containers reutilizáveis entre classes

```java
// Classe utilitária com containers compartilhados
public final class Containers {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withReuse(true);   // reutiliza o container entre execuções (local)

    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withReuse(true);

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}

// Em cada test class
@Testcontainers
class SomeServiceTest {

    static PostgreSQLContainer<?> postgres = Containers.POSTGRES;

    @Test
    void shouldWork() { ... }
}
```

`.withReuse(true)` requer `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

---

## GenericContainer — containers sem módulo dedicado

```java
@Container
static GenericContainer<?> valkey =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:7-alpine"))
                .withExposedPorts(6379)
                .withEnv("VALKEY_PASSWORD", "secret")
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

// Obter host/porta dinâmica
String host = valkey.getHost();
int port    = valkey.getMappedPort(6379);
```

---

## Wait strategies

```java
// HTTP health check
.waitingFor(Wait.forHttp("/health").forStatusCode(200)
        .withStartupTimeout(Duration.ofSeconds(60)))

// Log message
.waitingFor(Wait.forLogMessage(".*started.*", 1))

// Porta aberta (padrão para a maioria dos módulos)
.waitingFor(Wait.forListeningPort())

// Combinado
.waitingFor(new WaitAllStrategy()
        .withStrategy(Wait.forListeningPort())
        .withStrategy(Wait.forLogMessage(".*ready.*", 1))
        .withStartupTimeout(Duration.ofSeconds(90)))
```

---

## Rede entre containers

Use quando um container precisa se comunicar com outro dentro da rede Docker:

```java
@Testcontainers
class NetworkTest {

    static Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withNetwork(network)
                    .withNetworkAliases("db");

    @Container
    static GenericContainer<?> app =
            new GenericContainer<>("my-app:latest")
                    .withNetwork(network)
                    .withEnv("DB_HOST", "db")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));
}
```

---

## Módulos com suporte nativo

| Módulo | Artifact |
|--------|----------|
| PostgreSQL | `org.testcontainers:postgresql` |
| MySQL | `org.testcontainers:mysql` |
| MariaDB | `org.testcontainers:mariadb` |
| MongoDB | `org.testcontainers:mongodb` |
| Redis | `com.redis:testcontainers-redis` |
| Kafka | `org.testcontainers:kafka` |
| RabbitMQ | `org.testcontainers:rabbitmq` |
| LocalStack | `org.testcontainers:localstack` |
| Elasticsearch | `org.testcontainers:elasticsearch` |

---

## Regras

- Declare containers como `static` — iniciados uma vez por classe, não por teste
- Use `.withReuse(true)` localmente para acelerar o ciclo dev; desabilite no CI
- Nunca use H2 in-memory para testar SQL nativo, CTEs, ou features específicas de banco
- Use `Awaitility` para assertions assíncronas — nunca `Thread.sleep()`
- Marque testes de integração com `@Tag("integration")` e configure Surefire para executá-los separados
