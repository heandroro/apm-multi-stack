---
name: migrate-spring-to-quarkus
description: "Analyze a Spring Boot service and generate a step-by-step migration roadmap to Quarkus. Covers annotations, DI, JPA, configuration, testing, and native image preparation."
---

Analise o projeto Spring Boot e gere um roteiro de migração para Quarkus:

**Estilo Panache alvo**: $panache_style (Active Record | Repository)
**Native image**: $native (sim | não)
**Reactive**: $reactive (sim — usar Mutiny Uni/Multi | não — manter estilo imperativo)

Arquivos para inspecionar:
- `pom.xml` / `build.gradle` — dependências Spring Boot atuais
- `src/main/java/**/*Controller*.java` — endpoints REST
- `src/main/java/**/*Service*.java` — camada de serviço
- `src/main/java/**/*Repository*.java` — repositórios
- `src/main/java/**/*Entity*.java` / `@Entity` classes
- `src/main/resources/application.yml` / `application.properties`

Carregue [quarkus-core.instructions.md](../instructions/quarkus-core.instructions.md) para contexto de boas práticas.

---

## Mapa de equivalências Spring → Quarkus

| Spring Boot | Quarkus |
|-------------|---------|
| `@RestController` + `@RequestMapping` | `@Path` + `@GET`/`@POST`... (Jakarta REST) |
| `@Service` | `@ApplicationScoped` |
| `@Repository` | `@ApplicationScoped` + `PanacheRepositoryBase` |
| `@Component` | `@ApplicationScoped` |
| `@Autowired` | `@Inject` |
| `@Value` | `@ConfigProperty` |
| `@ConfigurationProperties` | `@ConfigMapping` |
| `@Transactional` | `@Transactional` (mesmo pacote `jakarta`) |
| `@SpringBootTest` | `@QuarkusTest` |
| `@MockBean` | `@InjectMock` |
| `ResponseEntity<T>` | `Uni<Response>` ou `Uni<T>` direto |
| `@ControllerAdvice` + `@ExceptionHandler` | `@ServerExceptionMapper` |
| `application.yml` | `application.properties` (ou YAML com extensão) |
| `spring-boot-starter-actuator` health | `quarkus-smallrye-health` |
| `springdoc-openapi` | `quarkus-smallrye-openapi` |
| `@Retryable` (Spring Retry) | `@Retry` (SmallRye Fault Tolerance) |

---

## Fase 1 — Dependências

- [ ] Substituir `spring-boot-starter-parent` pelo `quarkus-bom`
- [ ] Mapear cada `spring-boot-starter-*` para a extensão Quarkus equivalente:
  - `spring-boot-starter-web` → `quarkus-resteasy-reactive-jackson`
  - `spring-boot-starter-data-jpa` → `quarkus-hibernate-orm-panache` + driver JDBC
  - `spring-boot-starter-validation` → `quarkus-hibernate-validator`
  - `spring-boot-starter-actuator` → `quarkus-smallrye-health`
  - `spring-boot-starter-test` → `quarkus-junit5` + `rest-assured` + `quarkus-junit5-mockito`
- [ ] Remover `spring-cloud-*` — substituir por SmallRye Fault Tolerance se necessário
- [ ] Adicionar plugin `quarkus-maven-plugin`

---

## Fase 2 — Camada REST

Para cada `@RestController`:
- [ ] Trocar `@RestController` + `@RequestMapping("/path")` por `@Path("/path")` com `@Produces`/`@Consumes`
- [ ] Trocar `@GetMapping`, `@PostMapping`, etc. por `@GET`, `@POST`, etc.
- [ ] Trocar `@PathVariable` por `@PathParam`; `@RequestParam` por `@QueryParam`; `@RequestBody` por parâmetro direto
- [ ] Trocar `ResponseEntity<T>` por `Uni<Response>` (controle de status) ou `Uni<T>` (200 implícito)
- [ ] Substituir `@ControllerAdvice` / `@ExceptionHandler` por `@ServerExceptionMapper`

---

## Fase 3 — Injeção de Dependência

- [ ] Trocar `@Service` / `@Component` por `@ApplicationScoped`
- [ ] Trocar `@Autowired` (campo/construtor) por `@Inject`
- [ ] Trocar `@Value("${prop}")` por `@ConfigProperty(name = "prop")`
- [ ] Trocar `@ConfigurationProperties(prefix = "x")` por `@ConfigMapping(prefix = "x")`

---

## Fase 4 — Persistência (JPA → Panache)

- [ ] Entidades continuam com `@Entity`, `@Table`, `@Column` — sem mudança
- [ ] Se Active Record: fazer `extends PanacheEntityBase`; remover repository Spring Data
- [ ] Se Repository: fazer `implements PanacheRepositoryBase<Entity, IdType>`; trocar `JpaRepository` por `PanacheRepositoryBase`
- [ ] Trocar `repository.findById(id)` por `repository.findByIdOptional(id)` (retorna `Optional`)
- [ ] Ajustar JPQL se necessário — Panache usa HQL com parâmetros posicionais `?1`
- [ ] Remover `@EnableJpaRepositories` e `@EnableTransactionManagement` — Quarkus configura automaticamente

---

## Fase 5 — Configuração

- [ ] Converter `application.yml` para `application.properties` (ou manter YAML com `quarkus-config-yaml`)
- [ ] Trocar prefixos Spring (`spring.datasource.*`) por prefixos Quarkus (`quarkus.datasource.*`)
- [ ] Trocar `spring.profiles.active=dev` por `quarkus.profile=dev`
- [ ] Mover secrets para variáveis de ambiente (mesmo padrão)

---

## Fase 6 — Testes

- [ ] Trocar `@SpringBootTest` por `@QuarkusTest`
- [ ] Trocar `@MockBean` por `@InjectMock`
- [ ] Trocar `MockMvc` + `perform(get(...))` por RestAssured `given().when().get(...).then()`
- [ ] Remover configuração de datasource de teste — Dev Services sobe o banco automaticamente
- [ ] Adicionar `@QuarkusIntegrationTest` se `$native = sim`

---

## Fase 7 — Native image (se solicitado)

- [ ] Adicionar `@RegisterForReflection` em DTOs usados por Jackson fora do classpath Quarkus
- [ ] Verificar inicializadores estáticos — mover para `@PostConstruct` se acessarem recursos externos
- [ ] Declarar recursos de classpath em `quarkus.native.resources.includes`
- [ ] Configurar build nativo no CI: `-Pnative -Dquarkus.native.container-build=true`
- [ ] Adicionar `@QuarkusIntegrationTest` e rodar com `-Pnative`

---

## Output esperado

Para cada fase, liste:
1. **Arquivos afetados** no projeto atual
2. **Mudança necessária** — antes (Spring) × depois (Quarkus) em código
3. **Risco** — Baixo | Médio | Alto
4. **Verificação** — como confirmar que a mudança está correta

Ao final, gere uma **lista priorizada** ordenada por risco decrescente com estimativa de esforço.
