---
description: "Use when making HTTP requests in Java. Covers JDK HttpClient (Java 11+), Spring RestClient, Spring WebClient, @HttpExchange declarative clients, Apache HttpClient 5, OkHttp, and when to use each."
---

# HTTP Clients em Java — Guia Comparativo

## Quando usar cada client

| Client | Quando usar |
|--------|-------------|
| `java.net.http.HttpClient` | Sem framework; Java 11+; sync ou async com `CompletableFuture` |
| `RestClient` | Spring Boot 3.2+ síncrono; substituto direto do `RestTemplate` |
| `WebClient` | Spring WebFlux; chamadas reativas/não-bloqueantes; streaming |
| `@HttpExchange` | Spring 6+; interface declarativa (equivalente ao Feign); reduz boilerplate |
| `RestTemplate` | **Legado** — Spring 5/6; deprecated; **removido no Spring Boot 4** |
| Apache HttpClient 5 | Controle fino sobre pool, proxy, TLS; sem Spring |
| OkHttp | Android ou projetos não-Spring; interceptors; WebSocket |

---

## JDK `HttpClient` (Java 11+)

Sem dependências externas. Suporta HTTP/1.1 e HTTP/2.

```java
// Configuração — reutilize a instância (thread-safe)
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

// GET síncrono
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/users/123"))
        .header("Accept", "application/json")
        .GET()
        .timeout(Duration.ofSeconds(10))
        .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

if (response.statusCode() != 200) {
    throw new HttpClientException("Unexpected status: " + response.statusCode());
}

// Deserializar com Jackson
User user = objectMapper.readValue(response.body(), User.class);
```

```java
// POST com body JSON
String body = objectMapper.writeValueAsString(new CreateUserRequest("Ana", "ana@example.com"));

HttpRequest postRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/users"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
```

```java
// GET assíncrono com CompletableFuture (Java 11+)
CompletableFuture<User> future = client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body)
        .thenApply(json -> {
            try { return objectMapper.readValue(json, User.class); }
            catch (Exception e) { throw new UncheckedIOException((IOException) e); }
        });
```

**Quando usar:** serviços sem Spring; lambdas/scripts; Java 21 com Virtual Threads (bloqueia a virtual thread, não a plataforma).

---

## Spring `RestClient` (Spring Boot 3.2+)

API fluente, síncrona. Substitui `RestTemplate`. Disponível no `spring-boot-starter-web`.

```java
// Configuração como bean
@Bean
RestClient restClient(RestClient.Builder builder) {
    return builder
            .baseUrl("https://api.example.com")
            .defaultHeader("Accept", "application/json")
            .requestInterceptor(new LoggingInterceptor())
            .build();
}
```

```java
// GET — deserialização automática via Jackson
UserResponse user = restClient.get()
        .uri("/users/{id}", userId)
        .retrieve()
        .body(UserResponse.class);

// GET com lista
List<UserResponse> users = restClient.get()
        .uri("/users")
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});

// POST
UserResponse created = restClient.post()
        .uri("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateUserRequest("Ana", "ana@example.com"))
        .retrieve()
        .body(UserResponse.class);

// Tratamento de erro com onStatus
UserResponse result = restClient.get()
        .uri("/users/{id}", userId)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            throw new UserNotFoundException(userId);
        })
        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            throw new RemoteServiceException("Upstream failure: " + res.getStatusCode());
        })
        .body(UserResponse.class);
```

**Quando usar:** Spring Boot 3.2+ síncrono; é o client padrão para chamadas HTTP síncronas no Spring moderno.

---

## Spring `WebClient` (Spring WebFlux)

Reativo e não-bloqueante. Requer `spring-boot-starter-webflux`.

```java
@Bean
WebClient webClient(WebClient.Builder builder) {
    return builder
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
            .build();
}
```

```java
// GET reativo
Mono<UserResponse> user = webClient.get()
        .uri("/users/{id}", userId)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError,
                res -> res.bodyToMono(String.class)
                          .map(body -> new UserNotFoundException(userId)))
        .bodyToMono(UserResponse.class);

// GET lista
Flux<UserResponse> users = webClient.get()
        .uri("/users")
        .retrieve()
        .bodyToFlux(UserResponse.class);

// POST
Mono<UserResponse> created = webClient.post()
        .uri("/users")
        .bodyValue(new CreateUserRequest("Ana", "ana@example.com"))
        .retrieve()
        .bodyToMono(UserResponse.class);

// Bloquear em contexto não-reativo (use com moderação)
UserResponse result = user.block(Duration.ofSeconds(10));
```

**Quando usar:** projetos Spring WebFlux; streaming de dados; fanout de múltiplas chamadas em paralelo com `Mono.zip()`.

---

## `@HttpExchange` — Cliente declarativo (Spring 6+)

Interface anotada — Spring gera o proxy. Equivalente ao Feign, sem dependência extra.

```java
// Declarar a interface
@HttpExchange(url = "/users", accept = "application/json")
interface UserClient {

    @GetExchange("/{id}")
    UserResponse findById(@PathVariable UUID id);

    @GetExchange
    List<UserResponse> findAll();

    @PostExchange
    UserResponse create(@RequestBody CreateUserRequest request);

    @DeleteExchange("/{id}")
    void delete(@PathVariable UUID id);
}
```

```java
// Registrar o bean — usando RestClient (Spring Boot 3.2+)
@Bean
UserClient userClient(RestClient.Builder builder) {
    var restClient = builder.baseUrl("https://api.example.com").build();
    var factory = RestClientAdapter.create(restClient);
    return HttpServiceProxyFactory.builderFor(factory).build()
            .createClient(UserClient.class);
}

// Ou usando WebClient (reativo)
@Bean
UserClient userClientReactive(WebClient.Builder builder) {
    var webClient = builder.baseUrl("https://api.example.com").build();
    var factory = WebClientAdapter.create(webClient);
    return HttpServiceProxyFactory.builderFor(factory).build()
            .createClient(UserClient.class);
}
```

```java
// Uso no service — igual a qualquer bean
@Service
class OrderService {
    private final UserClient userClient;

    OrderService(UserClient userClient) { this.userClient = userClient; }

    public void validateUser(UUID userId) {
        var user = userClient.findById(userId);  // lança exceção se 4xx/5xx
        if (!user.active()) throw new UserInactiveException(userId);
    }
}
```

**Quando usar:** Spring 6+ com múltiplos endpoints de um mesmo serviço externo; elimina boilerplate de `RestClient`/`WebClient` repetitivo.

---

## Apache HttpClient 5

Sem Spring. Controle fino sobre pool de conexões, proxy, certificados TLS customizados.

```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.3.1</version>
</dependency>
```

```java
// Configuração com pool e timeouts
CloseableHttpClient client = HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(10)
                .build())
        .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(10))
                .build())
        .build();

// GET
try (CloseableHttpResponse response = client.execute(
        new HttpGet("https://api.example.com/users/123"))) {
    int status = response.getCode();
    String body = EntityUtils.toString(response.getEntity());
    // deserializar com Jackson
}

// Fechar o client ao encerrar a aplicação (use try-with-resources ou @PreDestroy)
```

**Quando usar:** sem Spring; proxy corporativo; mTLS (certificado cliente); pooling avançado.

---

## OkHttp

Popular em ambientes não-Spring; suporte a WebSocket; interceptors limpos.

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

```java
// Singleton — reutilize a instância
OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(chain -> {
            Request original = chain.request();
            Request withAuth = original.newBuilder()
                    .header("Authorization", "Bearer " + tokenProvider.getToken())
                    .build();
            return chain.proceed(withAuth);
        })
        .build();

// GET síncrono
Request request = new Request.Builder()
        .url("https://api.example.com/users/123")
        .build();

try (Response response = client.newCall(request).execute()) {
    if (!response.isSuccessful()) throw new IOException("Unexpected: " + response);
    String body = response.body().string();
}

// POST JSON
MediaType JSON = MediaType.get("application/json");
String json = objectMapper.writeValueAsString(payload);
Request post = new Request.Builder()
        .url("https://api.example.com/users")
        .post(RequestBody.create(json, JSON))
        .build();
```

**Quando usar:** projetos sem Spring; WebSocket; MockWebServer em testes.

---

## Testes

### MockWebServer (OkHttp) — agnóstico de framework

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

```java
class UserClientTest {

    MockWebServer server = new MockWebServer();

    @BeforeEach
    void setUp() throws IOException { server.start(); }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void shouldCallFindById() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"550e8400-e29b-41d4-a716-446655440000","name":"Ana"}
                        """));

        var baseUrl = server.url("/").toString();
        // instancie o client apontando para server.url(...)
        var client = buildClientFor(baseUrl);

        var result = client.findById(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result.name()).isEqualTo("Ana");
        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/users/550e8400-e29b-41d4-a716-446655440000");
    }
}
```

### `@RestClientTest` (Spring Boot) — para `RestClient` / `@HttpExchange`

```java
@RestClientTest(UserClient.class)
class UserClientSpringTest {

    @Autowired MockRestServiceServer server;
    @Autowired UserClient userClient;

    @Test
    void shouldReturnUser() {
        server.expect(requestTo("/users/123"))
              .andRespond(withSuccess("""
                      {"id":"123","name":"Ana"}
                      """, MediaType.APPLICATION_JSON));

        var result = userClient.findById(UUID.fromString("123"));
        assertThat(result.name()).isEqualTo("Ana");
    }
}
```

---

## Regras

- **Nunca** crie um `HttpClient` / `OkHttpClient` / `RestTemplate` por requisição — instâncias são caras e thread-safe; declare como bean ou singleton
- **Sempre defina timeouts** — `connectTimeout` e `readTimeout` / `responseTimeout`; sem timeout, uma lentidão upstream trava threads indefinidamente
- Trate erros HTTP explicitamente com `onStatus` — não dependa de exceção genérica para 4xx/5xx
- Use `@HttpExchange` para serviços externos com múltiplos endpoints — reduz boilerplate e facilita mock em testes
- `RestTemplate` está **removido** no Spring Boot 4 / Spring Framework 7 — migre para `RestClient`
- Não logue headers de `Authorization` ou `Cookie` — use interceptors/filters para mascarar antes de logar
