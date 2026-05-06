---
description: "Use when preparing a Quarkus application for GraalVM native image compilation. Covers @RegisterForReflection, resource inclusion, build configuration, native profiles, and common pitfalls."
---

# Quarkus Native Image — GraalVM

## Build commands

```bash
# Native com GraalVM instalado localmente
./mvnw package -Pnative

# Native via container (sem GraalVM local — recomendado em CI)
./mvnw package -Pnative -Dquarkus.native.container-build=true

# Especificar imagem de build (ex: mandrel para projetos Java puro)
./mvnw package -Pnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21

# Teste de integração native (sobe o binário nativo)
./mvnw verify -Pnative
```

```xml
<!-- pom.xml — profile native já gerado pelo CLI; confirme que está presente -->
<profiles>
    <profile>
        <id>native</id>
        <activation>
            <property><name>native</name></property>
        </activation>
        <properties>
            <skipITs>false</skipITs>
            <quarkus.native.enabled>true</quarkus.native.enabled>
        </properties>
    </profile>
</profiles>
```

---

## `@RegisterForReflection`

GraalVM remove código não acessível estaticamente. Use `@RegisterForReflection` para classes acessadas via reflection.

```java
// DTO serializado/desserializado por Jackson fora do classpath Quarkus
@RegisterForReflection
public record ExternalEventPayload(String type, String source, Map<String, Object> data) {}

// Classe utilitária com métodos acessados por nome em runtime
@RegisterForReflection(targets = { MyThirdPartyClass.class, AnotherClass.class })
public class NativeConfig {}

// Quando a classe está em uma lib externa e você não pode anotá-la
// Use no application.properties:
// quarkus.native.additional-native-image-build-args=--initialize-at-run-time=com.third.party.Class
```

**Quando é necessário:**
- Classes usadas por Jackson/JSON-B para deserialização dinâmica
- Enums acessados por `Enum.valueOf()` em lógica dinâmica
- Classes carregadas via `Class.forName()` ou `ServiceLoader`
- Beans CDI com escopo `@Dependent` sem ponto de injeção estático

---

## Inclusão de recursos (arquivos em classpath)

```properties
# application.properties — incluir arquivos no binário nativo
quarkus.native.resources.includes=templates/**,certs/*.pem,db/migration/*.sql

# Excluir recursos desnecessários para reduzir tamanho do binário
quarkus.native.resources.excludes=**/*.adoc,**/*.md
```

```java
// Ler recurso incluído no nativo
InputStream stream = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream("templates/email.html");
```

---

## Inicialização em runtime vs build time

```properties
# Forçar inicialização em runtime (evita erro de inicialização estática)
quarkus.native.additional-native-image-build-args=\
  --initialize-at-run-time=com.example.SomeClassWithStaticInit,\
  io.some.lib.RuntimeClass
```

**Regras:**
- Evite blocos `static {}` que acessam sistema de arquivos, rede ou variáveis de ambiente — use `@PostConstruct` ou inicialização lazy
- `System.getenv()` e `System.getProperty()` em static initializers causam falha no build nativo — mova para `@ConfigProperty`

---

## Serialização com Jackson

Quarkus usa `quarkus-jackson` que registra automaticamente módulos. Para tipos genéricos ou polimórficos:

```java
// Registrar módulo customizado
@Singleton
public class JacksonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
    }
}
```

```java
// Supertipo polimórfico — necessário @RegisterForReflection em cada subtipo
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "ORDER_CREATED"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = "ORDER_CANCELLED")
})
@RegisterForReflection
public sealed interface DomainEvent permits OrderCreatedEvent, OrderCancelledEvent {}

@RegisterForReflection
public record OrderCreatedEvent(UUID orderId, Instant occurredAt) implements DomainEvent {}
```

---

## Docker image para produção

```dockerfile
# Dockerfile.native (gerado pelo CLI — use como base)
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --chown=1001:root target/*-runner /work/application
RUN chmod 775 /work
EXPOSE 8080
USER 1001
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

```properties
# Tamanho e performance no nativo
quarkus.native.compression.level=5        # compressão UPX do binário (0=off, 10=max)
quarkus.native.enable-reports=true        # gera relatório de análise do binário
```

---

## Checklist antes do build nativo

- [ ] `@RegisterForReflection` em todos os DTOs usados fora do classpath Quarkus
- [ ] Recursos de classpath declarados em `quarkus.native.resources.includes`
- [ ] Sem `Class.forName()` dinâmico sem configuração `--initialize-at-run-time`
- [ ] Rodar `@QuarkusIntegrationTest` com `-Pnative` no CI antes de release
- [ ] Testar com `quarkus.native.enable-reports=true` para inspecionar código incluído

---

## Regras

- Nunca use `Class.forName()` ou `Method.invoke()` sem `@RegisterForReflection` correspondente
- Não acesse `System.getenv()` / `System.getProperty()` em inicializadores estáticos — use `@ConfigProperty`
- Prefira `quarkus.native.container-build=true` em CI — evita dependência de GraalVM no agente
- Execute `@QuarkusIntegrationTest` (`-Pnative`) antes de qualquer release de imagem nativa
