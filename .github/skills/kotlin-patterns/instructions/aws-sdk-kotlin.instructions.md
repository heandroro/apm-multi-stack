---
description: "Use when integrating Kotlin services with AWS. Covers AWS SDK for Kotlin (coroutine-native) for S3, SQS, SNS, DynamoDB, Secrets Manager, EventBridge. For Spring Boot + Kotlin projects, combine with Spring Cloud AWS 3.x."
---

# AWS SDK for Kotlin — Boas Práticas

> Use o **AWS SDK for Kotlin** (`aws.sdk.kotlin:*`) para código Kotlin puro ou Ktor.
> Para projetos **Spring Boot + Kotlin**, prefira **Spring Cloud AWS 3.x** (já integrado ao contexto Spring) e consulte `spring-cloud-aws.instructions.md` do `java-patterns` skill.
> O SDK Kotlin é coroutine-native — todas as operações são `suspend fun`, sem wrappers `async {}`.

## Dependências (Gradle Kotlin DSL)

```kotlin
// build.gradle.kts
val awsSdkKotlinVersion = "1.3.99" // verifique https://github.com/awslabs/aws-sdk-kotlin/releases

dependencies {
    // BOM — gerencie todas as versões de uma vez
    implementation(platform("aws.sdk.kotlin:bom:$awsSdkKotlinVersion"))

    // Adicione apenas os serviços que o projeto usa
    implementation("aws.sdk.kotlin:s3")
    implementation("aws.sdk.kotlin:sqs")
    implementation("aws.sdk.kotlin:sns")
    implementation("aws.sdk.kotlin:dynamodb")
    implementation("aws.sdk.kotlin:secretsmanager")
    implementation("aws.sdk.kotlin:eventbridge")

    // Testes com Testcontainers + LocalStack
    testImplementation("org.testcontainers:localstack:1.20.4")
}
```

## Configuração de credenciais e região

O SDK usa a mesma **credential provider chain** do AWS SDK v2:
1. Variáveis de ambiente `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION`
2. `~/.aws/credentials` (desenvolvimento local)
3. IAM Role (EC2 / ECS task role / Lambda execution role) — **preferido em produção**

```kotlin
// Cliente com configuração automática (resolve credenciais e região da chain)
val s3 = S3Client.fromEnvironment()

// Cliente com override de região e endpoint (LocalStack local)
val s3Local = S3Client {
    region = "us-east-1"
    endpointUrl = Url.parse("http://localhost:4566")
    credentialsProvider = StaticCredentialsProvider {
        accessKeyId = "test"
        secretAccessKey = "test"
    }
    forcePathStyle = true  // obrigatório para LocalStack
}
```

> **Regra de segurança**: nunca hardcode `accessKeyId` / `secretAccessKey` fora de testes locais.
> Em produção, use sempre `S3Client.fromEnvironment()` com IAM roles.

---

## S3

```kotlin
class DocumentStorageService(private val s3: S3Client) {

    suspend fun upload(bucket: String, key: String, bytes: ByteArray, contentType: String) {
        s3.putObject {
            this.bucket = bucket
            this.key = key
            body = ByteStream.fromBytes(bytes)
            this.contentType = contentType
        }
    }

    suspend fun download(bucket: String, key: String): ByteArray =
        s3.getObject(GetObjectRequest { this.bucket = bucket; this.key = key }) { response ->
            response.body?.toByteArray() ?: error("empty body for $key")
        }

    suspend fun delete(bucket: String, key: String) {
        s3.deleteObject {
            this.bucket = bucket
            this.key = key
        }
    }

    suspend fun presignedDownloadUrl(bucket: String, key: String, ttl: Duration): String {
        val presigner = S3Presigner.fromEnvironment()
        return presigner.presignGetObject {
            getObjectRequest {
                this.bucket = bucket
                this.key = key
            }
            expiresAfter = ttl
        }.url.toString()
    }
}
```

---

## SQS

```kotlin
class MessageQueueService(private val sqs: SqsClient) {

    // Produtor
    suspend fun send(queueUrl: String, payload: String, attributes: Map<String, String> = emptyMap()) {
        sqs.sendMessage {
            this.queueUrl = queueUrl
            messageBody = payload
            messageAttributes = attributes.mapValues {
                MessageAttributeValue { dataType = "String"; stringValue = it.value }
            }
        }
    }

    // Consumidor com polling manual (use em Ktor ou workers sem Spring)
    suspend fun poll(queueUrl: String, maxMessages: Int = 10): List<Message> =
        sqs.receiveMessage {
            this.queueUrl = queueUrl
            maxNumberOfMessages = maxMessages
            waitTimeSeconds = 20  // long polling — reduz custo de API
        }.messages ?: emptyList()

    suspend fun delete(queueUrl: String, receiptHandle: String) {
        sqs.deleteMessage {
            this.queueUrl = queueUrl
            this.receiptHandle = receiptHandle
        }
    }
}

// Padrão de consumo idiomático — process-then-delete
suspend fun processMessages(service: MessageQueueService, queueUrl: String) {
    val messages = service.poll(queueUrl)
    messages.forEach { msg ->
        try {
            handle(msg.body ?: return@forEach)
            service.delete(queueUrl, msg.receiptHandle!!)
        } catch (e: TransientException) {
            // não deleta — mensagem retorna para a fila após visibility timeout
        }
    }
}
```

---

## SNS

```kotlin
class EventPublisher(private val sns: SnsClient) {

    suspend fun publish(topicArn: String, message: String, subject: String? = null) {
        sns.publish {
            this.topicArn = topicArn
            this.message = message
            this.subject = subject
        }
    }
}
```

---

## DynamoDB

```kotlin
// Data class como entidade DynamoDB — use atributos explícitos
data class OrderItem(
    val orderId: String,
    val itemId: String,
    val productName: String,
    val quantity: Int,
)

class OrderItemRepository(private val dynamo: DynamoDbClient) {

    private val tableName = "order-items"

    suspend fun save(item: OrderItem) {
        dynamo.putItem {
            table = tableName
            this.item = mapOf(
                "orderId"     to AttributeValue.S(item.orderId),
                "itemId"      to AttributeValue.S(item.itemId),
                "productName" to AttributeValue.S(item.productName),
                "quantity"    to AttributeValue.N(item.quantity.toString()),
            )
        }
    }

    suspend fun findById(orderId: String, itemId: String): OrderItem? {
        val response = dynamo.getItem {
            table = tableName
            key = mapOf(
                "orderId" to AttributeValue.S(orderId),
                "itemId"  to AttributeValue.S(itemId),
            )
        }
        val item = response.item ?: return null
        return OrderItem(
            orderId     = item["orderId"]!!.asS(),
            itemId      = item["itemId"]!!.asS(),
            productName = item["productName"]!!.asS(),
            quantity    = item["quantity"]!!.asN().toInt(),
        )
    }
}
```

---

## Secrets Manager

```kotlin
class SecretsService(private val secretsManager: SecretsManagerClient) {

    suspend fun getSecret(secretId: String): String {
        val response = secretsManager.getSecretValue { this.secretId = secretId }
        return response.secretString ?: error("Secret $secretId is binary — not supported")
    }

    // Desserializa JSON do secret para um data class
    suspend inline fun <reified T> getSecretAs(secretId: String): T {
        val json = getSecret(secretId)
        return Json.decodeFromString<T>(json)
    }
}

// Uso típico com data class / record
@Serializable
data class DbCredentials(val url: String, val username: String, val password: String)

val creds = secretsService.getSecretAs<DbCredentials>("/myapp/prod/db")
```

---

## EventBridge

```kotlin
class DomainEventBus(private val eventBridge: EventBridgeClient) {

    suspend fun publish(
        eventBusName: String,
        source: String,
        detailType: String,
        detail: String,  // JSON serializado
    ) {
        eventBridge.putEvents {
            entries = listOf(
                PutEventsRequestEntry {
                    this.eventBusName = eventBusName
                    this.source = source
                    this.detailType = detailType
                    this.detail = detail
                }
            )
        }
    }
}
```

---

## Gestão de ciclo de vida dos clientes

Clientes do SDK são pesados — crie uma única instância e feche ao final da aplicação.

**Ktor:**
```kotlin
fun Application.configureAws() {
    val s3 = S3Client.fromEnvironment()
    val sqs = SqsClient.fromEnvironment()

    environment.monitor.subscribe(ApplicationStopped) {
        s3.close()
        sqs.close()
    }

    // Registre no DI (Koin, Kodein, etc.)
}
```

**Koin:**
```kotlin
val awsModule = module {
    single(createdAtStart = true) { S3Client.fromEnvironment() }
    single(createdAtStart = true) { SqsClient.fromEnvironment() }
    single { DocumentStorageService(get()) }
}
```

---

## Testes com LocalStack + Testcontainers

```kotlin
@Testcontainers
class S3ServiceTest {

    companion object {
        @Container
        @JvmStatic
        val localStack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.S3)

        lateinit var s3: S3Client

        @BeforeAll
        @JvmStatic
        fun setup() {
            s3 = S3Client {
                region = localStack.region
                endpointUrl = Url.parse(localStack.getEndpointOverride(S3).toString())
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = localStack.accessKey
                    secretAccessKey = localStack.secretKey
                }
                forcePathStyle = true
            }
        }
    }

    @Test
    fun `upload and download roundtrip`() = runTest {
        s3.createBucket { bucket = "test-bucket" }
        val content = "hello world".toByteArray()
        s3.putObject {
            bucket = "test-bucket"; key = "file.txt"
            body = ByteStream.fromBytes(content)
        }
        val result = s3.getObject(GetObjectRequest {
            bucket = "test-bucket"; key = "file.txt"
        }) { it.body?.toByteArray() }
        assertContentEquals(content, result)
    }
}
```

---

## Regras de segurança

- Nunca inclua `accessKeyId` / `secretAccessKey` em código commitado (apenas em testes com LocalStack)
- Use `S3Client.fromEnvironment()` em produção — resolve credenciais via IAM role automaticamente
- Prefira passar o `ScopedValue` / Ktor `ApplicationCall` para contexto de rastreamento em vez de `ThreadLocal`
- Secrets sensíveis (DB passwords, API keys) → Secrets Manager, não variáveis de ambiente diretamente
- Feche os clientes (`client.close()`) ao desligar a aplicação — evita leak de conexões
