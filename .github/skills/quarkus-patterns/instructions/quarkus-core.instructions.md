---
description: "Use when building Quarkus applications. Covers RESTEasy Reactive, Panache ORM (Active Record and Repository), CDI lifecycle, Mutiny reactive types, and configuration."
---

# Quarkus Core — RESTEasy Reactive, Panache, CDI, Mutiny

## Projeto base

```bash
quarkus create app com.example:my-service \
  --extension='resteasy-reactive-jackson,hibernate-orm-panache,jdbc-postgresql,smallrye-openapi,smallrye-health'
```

```xml
<!-- pom.xml — BOM Quarkus -->
<properties>
    <quarkus.platform.version>3.10.0</quarkus.platform.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## REST com RESTEasy Reactive

```java
@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService service;

    @GET
    @Path("/{id}")
    public Uni<OrderResponse> findById(@PathParam("id") UUID id) {
        return service.findById(id);
    }

    @GET
    public Multi<OrderResponse> list() {
        return service.listAll();
    }

    @POST
    public Uni<Response> create(@Valid CreateOrderRequest request) {
        return service.create(request)
                .map(order -> Response.status(Response.Status.CREATED).entity(order).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") UUID id) {
        return service.delete(id)
                .replaceWith(Response.noContent().build());
    }
}
```

### Tratamento de erros centralizado

```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<ApplicationException> {

    @Override
    public Response toResponse(ApplicationException ex) {
        return Response.status(ex.getStatus())
                .entity(new ErrorResponse(ex.getCode(), ex.getMessage()))
                .build();
    }
}

// Ou com @ServerExceptionMapper (RESTEasy Reactive)
public class ExceptionHandlers {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        return RestResponse.status(Response.Status.NOT_FOUND,
                new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage()));
    }
}
```

---

## Panache — Active Record (padrão Quarkus)

```java
@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderStatus status;

    @CreationTimestamp
    public Instant createdAt;

    // Queries estáticas no modelo
    public static Optional<Order> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }

    public static List<Order> findByCustomer(UUID customerId) {
        return list("customerId = ?1 ORDER BY createdAt DESC", customerId);
    }

    public static long countByStatus(OrderStatus status) {
        return count("status", status);
    }
}
```

## Panache — Repository Pattern

Prefira o Repository quando quiser separar queries da entidade de domínio.

```java
@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<Order, UUID> {

    public Optional<Order> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }

    public List<Order> findByCustomer(UUID customerId) {
        return list("customerId = ?1 ORDER BY createdAt DESC", customerId);
    }

    public PanacheQuery<Order> findByStatus(OrderStatus status) {
        return find("status", status);
    }
}
```

---

## CDI — Escopos e Injeção

| Escopo | Quando usar |
|--------|-------------|
| `@ApplicationScoped` | Singleton — serviços, repositórios, clientes HTTP |
| `@RequestScoped` | Um bean por requisição HTTP |
| `@Dependent` | Padrão — novo bean por ponto de injeção |
| `@Singleton` | Singleton Jakarta (sem proxy) — use `@ApplicationScoped` para maior flexibilidade |

```java
@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository repository;   // ou injeção via construtor (preferível)

    // Injeção por construtor — mais testável
    // @Inject
    // OrderService(OrderRepository repository) { this.repository = repository; }

    @Transactional
    public Uni<OrderResponse> create(CreateOrderRequest request) {
        var order = new Order();
        order.customerId = request.customerId();
        order.status = OrderStatus.PENDING;
        return repository.persist(order)
                .map(OrderResponse::from);
    }

    public Uni<OrderResponse> findById(UUID id) {
        return Uni.createFrom().optional(repository.findByIdOptional(id))
                .onItem().ifNull().failWith(() -> new OrderNotFoundException(id))
                .map(OrderResponse::from);
    }
}
```

---

## Mutiny — Uni e Multi

```java
// Uni — zero ou um resultado (equivalente a Mono)
Uni<Order> uni = Uni.createFrom().item(order);
Uni<Order> fromOptional = Uni.createFrom().optional(Optional.of(order));
Uni<Void> voidUni = Uni.createFrom().voidItem();

// Transformações
uni
    .map(OrderResponse::from)                         // transformar item
    .onFailure(OrderNotFoundException.class)
        .recoverWithNull()                            // tratar exceção específica
    .onItem().ifNull().failWith(NotFoundException::new);

// Multi — zero ou N resultados (equivalente a Flux)
Multi<Order> multi = Multi.createFrom().iterable(orders);
multi
    .filter(o -> o.status == OrderStatus.PENDING)
    .map(OrderResponse::from)
    .collect().asList()                               // Multi → Uni<List>
    .await().indefinitely();                          // bloquear (somente em testes)

// Combinar Uni em paralelo
Uni.combine().all().unis(uni1, uni2)
    .asTuple()
    .map(t -> buildResponse(t.getItem1(), t.getItem2()));
```

**Regras Mutiny:**
- Nunca bloqueie com `.await().indefinitely()` em código de produção — somente em testes
- Use `.onFailure().recoverWith*()` para tratar erros de forma declarativa
- Prefira `Uni<Response>` no resource; converta para `Multi<T>` somente para streaming real

---

## Configuração

```java
// application.properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=${DB_URL}
quarkus.hibernate-orm.database.generation=validate
quarkus.hibernate-orm.log.sql=false
```

```java
// Injetar configuração tipada
@ConfigProperty(name = "app.feature.new-checkout", defaultValue = "false")
boolean newCheckoutEnabled;

// Ou com @ConfigMapping para grupos de propriedades
@ConfigMapping(prefix = "app.payment")
public interface PaymentConfig {
    String gateway();
    Duration timeout();
    int maxRetries();
}
```

---

## Regras

- Use `@ApplicationScoped` por padrão — nunca `new` para beans CDI
- `@Transactional` no service, não no resource
- Prefira `Uni<Response>` para controle de status code; `Uni<T>` quando o status é sempre 200
- Nunca bloqueie a event loop — `await()` somente em `@QuarkusTest`
- `@Enumerated(EnumType.STRING)` sempre — nunca `ORDINAL`
