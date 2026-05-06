---
description: "Use when working with Spring Data JPA. Covers entity mapping, repository patterns, JPQL/native queries, projections, pagination, auditing, and Testcontainers integration."
---

# Spring Data JPA — Boas Práticas

## Dependências

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

```yaml
# application.yml
spring:
  jpa:
    open-in-view: false          # SEMPRE desabilitar — evita lazy loading acidental fora da transação
    hibernate:
      ddl-auto: validate         # 'validate' em produção; 'create-drop' somente em testes
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
```

---

## Entidades

```java
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)   // sempre STRING, nunca ORDINAL
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)   // LAZY é o padrão — deixe explícito
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    // Construtor protegido obrigatório para JPA
    protected Order() {}

    public Order(UUID customerId, OrderStatus status) {
        this.customerId = customerId;
        this.status = status;
    }

    // Getters apenas — entidade não deve ter setters públicos arbitrários
    // Use métodos de domínio com nome de negócio
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }
}
```

**Regras de entidade:**
- `@Enumerated(EnumType.STRING)` — nunca `ORDINAL` (quebra ao reordenar o enum)
- `fetch = FetchType.LAZY` em `@ManyToOne` e `@OneToMany` — evite `EAGER`
- Construtor `protected` sem argumentos para JPA; construtor público com campos obrigatórios para o domínio
- Evite getters/setters genéricos — prefira métodos de negócio (`confirm()`, `cancel()`, `addItem()`)
- `@GeneratedValue(strategy = GenerationType.UUID)` para UUIDs (Hibernate 6+)

---

## Auditing

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;
}

// Habilitar no @SpringBootApplication ou em uma @Configuration
@EnableJpaAuditing
```

```java
// Fornecer o usuário atual para @CreatedBy / @LastModifiedBy
@Bean
AuditorAware<String> auditorProvider() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(Authentication::getName);
}
```

---

## Repositórios

```java
// Repositório básico — derive queries pelo nome do método quando possível
interface OrderRepository extends JpaRepository<Order, UUID> {

    // Query derivada — simples, sem JPQL
    List<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    boolean existsByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    // Ordenação inline
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    // JPQL explícito — use para lógica que o nome do método não expressa bem
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt >= :since")
    List<Order> findRecentByStatus(@Param("status") OrderStatus status,
                                   @Param("since") Instant since);

    // Native query — somente quando JPQL não suporta a feature do banco
    @Query(value = "SELECT * FROM orders WHERE status = :status LIMIT :limit",
           nativeQuery = true)
    List<Order> findTopByStatus(@Param("status") String status, @Param("limit") int limit);

    // Modifying — para UPDATE/DELETE sem carregar entidades
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("newStatus") OrderStatus newStatus);
}
```

**Regras de repositório:**
- Use `@Modifying` + `@Query` para updates/deletes em massa — nunca carregue N entidades para atualizar
- `@Modifying` limpa o cache do persistence context por padrão (`clearAutomatically = true` no Spring Data 3+)
- Prefira queries derivadas para condições simples; JPQL para lógica complexa; native query como último recurso

---

## Projeções (DTOs via interface ou record)

Evite retornar entidades diretamente de endpoints. Use projeções para buscar somente os campos necessários.

```java
// Projeção por interface — Spring Data gera o proxy automaticamente
interface OrderSummary {
    UUID getId();
    OrderStatus getStatus();
    Instant getCreatedAt();
}

interface OrderRepository extends JpaRepository<Order, UUID> {
    List<OrderSummary> findByCustomerId(UUID customerId);
}
```

```java
// Projeção por record (JPQL constructor expression) — preferível para serialização
public record OrderSummary(UUID id, OrderStatus status, Instant createdAt) {}

@Query("SELECT new com.example.OrderSummary(o.id, o.status, o.createdAt) FROM Order o WHERE o.customerId = :customerId")
List<OrderSummary> findSummariesByCustomerId(@Param("customerId") UUID customerId);
```

```java
// @NamedEntityGraph — para evitar N+1 em relacionamentos
@NamedEntityGraph(
    name = "Order.withItems",
    attributeNodes = @NamedAttributeNode("items")
)
@Entity
public class Order { ... }

// No repositório:
@EntityGraph("Order.withItems")
List<Order> findWithItemsByCustomerId(UUID customerId);
```

---

## Paginação e ordenação

```java
// Paginação via Pageable
Page<OrderSummary> findByStatus(OrderStatus status, Pageable pageable);

// Controller
@GetMapping
Page<OrderSummary> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sort) {
    var pageable = PageRequest.of(page, size, Sort.by(sort).descending());
    return service.list(status, pageable);
}
```

---

## Service layer — transações

```java
@Service
@Transactional(readOnly = true)   // padrão somente-leitura para toda a classe
class OrderService {

    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public OrderResponse findById(UUID id) {
        return repository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional   // override para operações de escrita
    public OrderResponse create(CreateOrderRequest request) {
        var order = new Order(request.customerId(), OrderStatus.PENDING);
        return OrderResponse.from(repository.save(order));
    }

    @Transactional
    public void confirm(UUID id) {
        var order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.confirm();   // dirty checking — não precisa de save() explícito
        // repository.save(order)  ← desnecessário quando a entidade já está no contexto
    }
}
```

**Regras de transação:**
- `@Transactional(readOnly = true)` na classe — evita flush desnecessário, habilita otimizações no driver
- `@Transactional` sem `readOnly` somente nos métodos que escrevem
- Aproveite **dirty checking** — não chame `save()` em entidades já gerenciadas que foram modificadas
- Nunca propague entidades gerenciadas para fora da transação — converta para DTOs antes de retornar

---

## Testes com `@DataJpaTest`

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Replace.NONE para usar Testcontainers em vez de H2
class OrderRepositoryTest {

    @Container
    @ServiceConnection   // Spring Boot 3.x — configura datasource automaticamente
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    OrderRepository repository;

    @Autowired
    TestEntityManager em;

    @Test
    void shouldFindByCustomerIdAndStatus() {
        var customerId = UUID.randomUUID();
        em.persist(new Order(customerId, OrderStatus.PENDING));
        em.flush();

        var result = repository.findByCustomerIdAndStatus(customerId, OrderStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        var result = repository.findByCustomerIdAndStatus(UUID.randomUUID(), OrderStatus.PENDING);
        assertThat(result).isEmpty();
    }
}
```

---

## Regras gerais

- **`open-in-view: false`** sempre — lazy loading silencioso fora da transação causa N+1 difícil de detectar
- **`ddl-auto: validate`** em produção; use Flyway ou Liquibase para migrações de schema
- Nunca use `@Enumerated(EnumType.ORDINAL)` — quebra ao reordenar valores do enum
- Nunca use `FetchType.EAGER` — sempre `LAZY`; use `@EntityGraph` quando precisar de join fetch pontual
- Nunca exponha entidades JPA diretamente em controllers — converta para DTOs/records
- Use `@Transactional(readOnly = true)` como padrão na classe de serviço
