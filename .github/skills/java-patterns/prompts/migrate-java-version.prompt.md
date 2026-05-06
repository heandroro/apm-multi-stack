---
name: migrate-java-version
description: "Analyze Java code and suggest idiomatic rewrites for a target Java version. Identifies outdated patterns and shows modern equivalents."
---

Analise o seguinte código Java e sugira reescritas idiomáticas para a versão alvo:

**Código atual**:
```java
$code
```

**Versão atual estimada**: $source_version (ex: Java 8 | Java 11 | Java 17)
**Versão alvo**: $target_version (ex: Java 17 | Java 21 | Java 25)

## O que analisar

### Se migrando para Java 17

Carregue [java-17.instructions.md](../instructions/java-17.instructions.md) e identifique:

- [ ] Classes POJO imutáveis → substitua por `record`
- [ ] Hierarquias polimórficas fechadas → substitua por `sealed interface`
- [ ] `if (x instanceof Foo) { Foo f = (Foo) x; }` → use pattern matching `instanceof`
- [ ] Strings multi-linha com `+` ou `String.format` → use text blocks `"""`
- [ ] `switch` statements com variável → use `switch` expressions
- [ ] `.collect(Collectors.toUnmodifiableList())` → use `.toList()`

### Se migrando para Java 21

Carregue [java-21.instructions.md](../instructions/java-21.instructions.md) e identifique:

- [ ] `CompletableFuture` / callbacks para I/O → avalie Virtual Threads com código bloqueante simples
- [ ] `ExecutorService` com pool fixo para I/O → use `Executors.newVirtualThreadPerTaskExecutor()`
- [ ] `.get(0)` / `.get(list.size() - 1)` → use `getFirst()` / `getLast()`
- [ ] `instanceof` chain com vários tipos → use switch pattern matching com `when`
- [ ] Desestruturação manual de records → use record patterns

### Se migrando para Java 25

Carregue [java-25.instructions.md](../instructions/java-25.instructions.md) e identifique:

- [ ] `String.format()` ou `.formatted()` → use String Templates (`STR."..."`)
- [ ] `catch (Exception e)` onde `e` nunca é usado → use `catch (Exception _)`
- [ ] `ThreadLocal` → avalie Scoped Values para virtual threads
- [ ] Múltiplos `import` do mesmo módulo → considere `import module java.base`
- [ ] Validação antes de `super()` em construtor → use flexible constructor bodies

### Migrando Spring Cloud AWS 2.x → 3.x (AWS SDK v1 → SDK v2)

Carregue [spring-cloud-aws.instructions.md](../instructions/spring-cloud-aws.instructions.md) e identifique:

- [ ] `com.amazonaws:aws-java-sdk-s3` + `AmazonS3` → `io.awspring.cloud:spring-cloud-aws-starter-s3` + `S3Template`
- [ ] `com.amazonaws:aws-java-sdk-sqs` + `AmazonSQS` / `@SqsListener` (v2 antigo) → starter-sqs 3.x + `SqsTemplate` / `@SqsListener` (namespace `io.awspring`)
- [ ] `com.amazonaws:aws-java-sdk-sns` + `AmazonSNS` → `spring-cloud-aws-starter-sns` + `SnsTemplate`
- [ ] `com.amazonaws:aws-java-sdk-dynamodb` + `DynamoDBMapper` + `@DynamoDBTable` → `spring-cloud-aws-starter-dynamodb` + `@DynamoDbBean` + `DynamoDbTemplate`
- [ ] `@EnableSqs` / `@EnableSns` removidos — auto-configuration no Spring Cloud AWS 3.x
- [ ] Secrets em `bootstrap.yml` via `aws.secretsmanager.secret-name` → `spring.config.import: aws-secretsmanager:<secret-path>`
- [ ] `AWSCredentialsProvider` hardcoded → remover; usar IAM roles ou variáveis de ambiente
- [ ] `AmazonSQSAsync` + polling manual → `@SqsListener` com configuração de concorrência declarativa
- [ ] `com.amazonaws:aws-java-sdk-eventbridge` → `spring-cloud-aws-starter-eventbridge` + `EventBridgeTemplate`

## Output esperado

Para cada oportunidade encontrada:
1. **Antes** — código original
2. **Depois** — código reescrito com a feature do Java alvo ou Spring Cloud AWS 3.x
3. **Feature usada** — nome da feature e versão em que ficou GA (ou versão da lib)
4. **Impacto** — legibilidade | segurança de tipos | performance | redução de boilerplate

Ao final, liste quais features requerem `--enable-preview` na versão alvo e quais dependências Maven devem ser removidas/adicionadas.
