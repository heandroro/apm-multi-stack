---
description: "Use when integrating a Spring Boot 3.x service with AWS. Covers Spring Cloud AWS 3.x (SDK v2) for S3, SQS, SNS, DynamoDB, Secrets Manager, Parameter Store, ElastiCache (Redis), and EventBridge. Requires Java 17+."
---

# Spring Cloud AWS 3.x — Boas Práticas

> Requer Spring Boot 3.x (mínimo), Java 17+ e AWS SDK v2.
> **Nunca** use `com.amazonaws:aws-java-sdk-*` (SDK v1) em projetos novos — use `software.amazon.awssdk`.

## BOM e dependências (Maven)

```xml
<!-- pom.xml — importe o BOM no <dependencyManagement> -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-dependencies</artifactId>
            <version>3.2.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Adicione apenas os starters que o serviço precisa -->
<dependencies>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-s3</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-sqs</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-sns</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-dynamodb</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-secrets-manager</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-parameter-store</artifactId>
    </dependency>
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-eventbridge</artifactId>
    </dependency>
    <!-- ElastiCache — via Spring Data Redis + Lettuce (sem starter dedicado no 3.x) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

## Configuração base (`application.yml`)

```yaml
spring:
  cloud:
    aws:
      region:
        static: us-east-1          # ou use 'auto' para EC2/ECS/Lambda
      credentials:
        # Prefira IAM roles (EC2/ECS/Lambda) — evite access-key em código
        # Para desenvolvimento local: aws configure ou variáveis de ambiente
        # AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN
      stack:
        auto: false                 # desabilita CloudFormation stack detection
```

> **Regra de segurança**: nunca coloque `access-key` ou `secret-key` em `application.yml` commitados. Use variáveis de ambiente, AWS Secrets Manager ou IAM instance roles.

---

## S3

```java
@Service
class DocumentStorageService {
    private final S3Template s3Template;

    DocumentStorageService(S3Template s3Template) {
        this.s3Template = s3Template;
    }

    S3Resource upload(String bucket, String key, InputStream content, String contentType) {
        var objectRequest = UploadRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build();
        return s3Template.upload(bucket, key, content, objectRequest);
    }

    InputStream download(String bucket, String key) {
        return s3Template.download(bucket, key).getInputStream();
    }

    void delete(String bucket, String key) {
        s3Template.deleteObject(bucket, key);
    }

    // Presigned URL para download temporário (15 minutos)
    URL presignedDownloadUrl(String bucket, String key) {
        return s3Template.createSignedGetURL(bucket, key, Duration.ofMinutes(15));
    }
}
```

```yaml
spring:
  cloud:
    aws:
      s3:
        path-style-access-enabled: false  # true somente para LocalStack local
```

---

## SQS

### Produtor

```java
@Service
class NotificationSender {
    private final SqsTemplate sqsTemplate;

    NotificationSender(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    void send(String queueUrl, OrderEvent event) {
        sqsTemplate.send(to -> to
                .queue(queueUrl)
                .payload(event)
                .messageGroupId(event.orderId())         // FIFO queues
                .messageDeduplicationId(event.eventId()) // FIFO queues
        );
    }
}
```

### Consumidor

```java
@Component
class OrderEventListener {

    // Acknowledgement automático (padrão) — mensagem deletada após retorno sem exceção
    @SqsListener("${aws.sqs.order-events-queue-url}")
    void onOrderEvent(OrderEvent event) {
        process(event);
    }

    // Acknowledgement manual — útil para controle de retry granular
    @SqsListener(value = "${aws.sqs.order-events-queue-url}",
                 acknowledgementMode = SqsListenerAcknowledgementMode.MANUAL)
    void onOrderEventManual(OrderEvent event, Acknowledgement acknowledgement) {
        try {
            process(event);
            acknowledgement.acknowledge();
        } catch (TransientException e) {
            throw e; // Não acknowledge — mensagem retorna para a fila
        }
    }
}
```

```yaml
spring:
  cloud:
    aws:
      sqs:
        listener:
          max-concurrent-messages: 10
          max-messages-per-poll: 10
          poll-timeout: 20s
```

---

## SNS

```java
@Service
class EventPublisher {
    private final SnsTemplate snsTemplate;

    EventPublisher(SnsTemplate snsTemplate) {
        this.snsTemplate = snsTemplate;
    }

    void publish(String topicArn, DomainEvent event) {
        snsTemplate.sendNotification(topicArn, event, event.getClass().getSimpleName());
    }
}
```

---

## DynamoDB

### Entidade

```java
@DynamoDbBean
public class OrderItem {
    private String orderId;   // Partition key
    private String itemId;    // Sort key
    private String productName;
    private int quantity;

    @DynamoDbPartitionKey
    public String getOrderId() { return orderId; }

    @DynamoDbSortKey
    public String getItemId() { return itemId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "product-index")
    public String getProductName() { return productName; }

    public int getQuantity() { return quantity; }

    public void setOrderId(String v) { this.orderId = v; }
    public void setItemId(String v) { this.itemId = v; }
    public void setProductName(String v) { this.productName = v; }
    public void setQuantity(int v) { this.quantity = v; }
}
```

### Repositório

```java
@Repository
class OrderItemRepository {
    private final DynamoDbTemplate dynamoDbTemplate;
    private final DynamoDbEnhancedClient enhancedClient;

    OrderItemRepository(DynamoDbTemplate dynamoDbTemplate,
                        DynamoDbEnhancedClient enhancedClient) {
        this.dynamoDbTemplate = dynamoDbTemplate;
        this.enhancedClient = enhancedClient;
    }

    OrderItem save(OrderItem item) {
        return dynamoDbTemplate.save(item);
    }

    Optional<OrderItem> findById(String orderId, String itemId) {
        return Optional.ofNullable(dynamoDbTemplate.load(
                Key.builder().partitionValue(orderId).sortValue(itemId).build(),
                OrderItem.class));
    }

    List<OrderItem> findByProduct(String productName) {
        var table = enhancedClient.table("order-items", TableSchema.fromBean(OrderItem.class));
        return table.index("product-index")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(k -> k.partitionValue(productName))))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }
}
```

---

## Secrets Manager

```yaml
# application.yml — resolução automática via bootstrap
spring:
  config:
    import:
      - "aws-secretsmanager:/myapp/production/db-credentials"
      - "aws-secretsmanager:/myapp/production/api-keys?failFast=true"
```

```java
@ConfigurationProperties(prefix = "db")
public record DatabaseCredentials(String url, String username, String password) {}
```

O secret deve ter formato JSON: `{ "db.url": "jdbc:...", "db.username": "app", "db.password": "secret" }`

---

## Parameter Store

```yaml
spring:
  config:
    import:
      - "aws-parameterstore:/myapp/production/"
```

---

## ElastiCache (Redis)

```yaml
spring:
  data:
    redis:
      host: ${ELASTICACHE_ENDPOINT}
      port: 6379
      ssl:
        enabled: true   # obrigatório em produção
      timeout: 2000ms
```

```java
@Bean
RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    var config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    return RedisCacheManager.builder(factory).cacheDefaults(config).build();
}
```

---

## EventBridge

```java
@Service
class DomainEventBus {
    private final EventBridgeTemplate eventBridgeTemplate;

    DomainEventBus(EventBridgeTemplate eventBridgeTemplate) {
        this.eventBridgeTemplate = eventBridgeTemplate;
    }

    void publish(String eventBusName, String source, String detailType, Object detail) {
        eventBridgeTemplate.send(eventBusName, Event.of(source, detailType, detail));
    }
}
```

---

## Testes com LocalStack + Testcontainers

```java
@SpringBootTest
@Testcontainers
class S3IntegrationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static",                    localStack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key",           localStack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key",           localStack::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint",                      localStack::getEndpoint);
        registry.add("spring.cloud.aws.s3.path-style-access-enabled",     () -> "true");
    }

    @Autowired S3Template s3Template;

    @Test
    void uploadAndDownload() throws Exception {
        s3Template.createBucket("test-bucket");
        s3Template.upload("test-bucket", "doc.txt",
                new ByteArrayInputStream("hello".getBytes()));
        var result = s3Template.download("test-bucket", "doc.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("hello");
    }
}
```

---

## Regras de segurança

- **Nunca** coloque `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` em arquivos commitados
- Em ECS/EKS/Lambda: use IAM Task Role / Execution Role — sem credenciais explícitas
- Em desenvolvimento local: `aws configure` (profile) ou variáveis de ambiente
- Secrets sensíveis → Secrets Manager; configuração → Parameter Store
- Habilite `ssl.enabled: true` no ElastiCache em produção obrigatoriamente
- Nunca exponha ARNs de recursos AWS em logs de aplicação
