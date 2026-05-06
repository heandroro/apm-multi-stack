---
name: migrate-spring-boot
description: "Analyze a Spring Boot project and generate a step-by-step migration roadmap between major versions (e.g. 3.x → 4.x). Covers Spring Framework, Jakarta EE, Security, Data, and build configuration changes."
---

Analise o projeto Spring Boot e gere um roteiro de migração passo a passo:

**Versão atual**: $source_version (ex: 3.3 | 3.4)
**Versão alvo**: $target_version (ex: 4.0 | 4.1)

Arquivos para inspecionar (quando disponíveis):
- `pom.xml` / `build.gradle` — versões de dependências e plugins
- `src/main/resources/application.yml` / `application.properties`
- `src/main/java/**/*SecurityConfig*.java` — configuração de segurança
- `src/main/java/**/*Config*.java` — demais `@Configuration`

Carregue [spring-boot.instructions.md](../instructions/spring-boot.instructions.md) e [spring-cloud-aws.instructions.md](../instructions/spring-cloud-aws.instructions.md) para contexto de boas práticas.

---

## Spring Boot 3.x → 4.x

> Spring Boot 4.0 requer **Java 21** como mínimo (Java 17 não é mais suportado).
> Baseado em Spring Framework 7 e Jakarta EE 11.

### Fase 0 — Pré-requisitos

Antes de atualizar o Spring Boot, garanta:

- [ ] Java 21+ instalado e configurado como `java.version` no `pom.xml` / toolchain no `build.gradle`
- [ ] Todas as warnings de deprecation do Spring Boot 3.x foram resolvidas (rode com `-Xlint:deprecation`)
- [ ] Cobertura de testes ≥ 70% — a migração vai quebrar coisas; precisa de rede de segurança

```xml
<!-- pom.xml — versão mínima Java antes de migrar -->
<properties>
    <java.version>21</java.version>
</properties>
```

---

### Fase 1 — Atualizar versões base

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>  <!-- ajuste para a versão alvo -->
</parent>
```

- [ ] Atualizar `spring-cloud-aws-dependencies` para a versão compatível com Spring Boot 4 (verifique [start.spring.io](https://start.spring.io))
- [ ] Atualizar `spring-cloud-dependencies` (BOM) se usar Spring Cloud
- [ ] Atualizar plugins: `maven-compiler-plugin` ≥ 3.13, `spring-boot-maven-plugin`
- [ ] Rodar `mvn dependency:tree` e checar conflitos de versão

---

### Fase 2 — Jakarta EE 11

Spring Boot 4 usa Jakarta EE 11 (Spring Boot 3 usa EE 10). Mudanças impactantes:

| Pacote anterior (EE 10) | Pacote novo (EE 11) | O que mudou |
|------------------------|--------------------|-|
| `jakarta.persistence 3.1` | `jakarta.persistence 3.2` | `find()` retorna `Optional<T>`; novos métodos em `EntityManager` |
| `jakarta.servlet 6.0` | `jakarta.servlet 6.1` | Novos métodos em `HttpServletRequest` |
| `jakarta.validation 3.0` | `jakarta.validation 3.1` | Suporte a generics em constraints |

- [ ] `EntityManager.find()` agora retorna `Optional<T>` — retire os wrappers `Optional.ofNullable()` redundantes
- [ ] Verifique constraints de validação customizadas que implementam `ConstraintValidator<A, T>` — ajuste se `T` usava generics
- [ ] Atualize `persistence.xml` se necessário (Jakarta Persistence 3.2)

---

### Fase 3 — Spring Framework 7

- [ ] **Namespace `@HttpExchange`**: interfaces declarativas de HTTP client (equivalente ao `@FeignClient`) são GA — avalie migrar de OpenFeign para `@HttpExchange` + `RestClient`
- [ ] **`RestTemplate` removido**: substitua por `RestClient` (síncrono) ou `WebClient` (reativo)
- [ ] **`MockMvc` fluent API**: nova API fluente — ajuste testes `mockMvc.perform(...)` se usar o estilo antigo
- [ ] **`@RequestParam` required default**: comportamento pode ter mudado para campos de `record` — verifique controllers
- [ ] **Observability**: `Micrometer 1.14+` — verifique se métricas customizadas precisam de ajuste de API

```java
// Antes — RestTemplate (removido no Spring 7)
RestTemplate template = new RestTemplate();
User user = template.getForObject("/users/{id}", User.class, id);

// Depois — RestClient
RestClient client = RestClient.create();
User user = client.get()
        .uri("/users/{id}", id)
        .retrieve()
        .body(User.class);
```

---

### Fase 4 — Spring Security 7

Spring Security 7 é a maior fonte de quebras em migrações.

- [ ] **`WebSecurityConfigurerAdapter` removido definitivamente** (já deprecado no 5.7, removido no 6 — mas verifique se alguma lib ainda o usa indiretamente)
- [ ] **Novo `SecurityFilterChain`**: confirme que o bean está correto

```java
// Padrão Spring Security 7 (Spring Boot 4)
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
```

- [ ] **`PasswordEncoder` padrão**: verifique se o algoritmo padrão mudou — rode testes de autenticação
- [ ] **CSRF**: comportamento padrão pode ter mudado para APIs REST — confirme se `csrf().disable()` ainda é necessário ou se a nova política é mais restritiva
- [ ] **`SecurityMockMvcRequestPostProcessors`**: API de testes de segurança pode ter mudado — verifique imports

---

### Fase 5 — Spring Data 4

- [ ] **`CrudRepository.findById()`**: já retorna `Optional<T>` desde Spring Data 2 — nenhuma mudança
- [ ] **`Pageable` e `Page`**: sem mudanças de API; verifique se os testes de repository ainda passam
- [ ] **JPQL / HQL**: Spring Data 4 usa Hibernate 7 — verifique queries JPQL com funções de banco específicas
- [ ] **`@Query` com `nativeQuery = true`**: testes de integração obrigatórios

---

### Fase 6 — Propriedades renomeadas / removidas

Execute o [Spring Boot Properties Migrator](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-application-properties.html) para detectar propriedades obsoletas automaticamente:

```xml
<!-- Adicione temporariamente — remova após migração -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

Propriedades comuns com mudanças:

| Propriedade antiga | Propriedade nova |
|-------------------|-----------------|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Sem mudança — mas valide o formato |
| `spring.datasource.hikari.*` | Verifique HikariCP 6.x changelogs |
| `management.endpoints.web.exposure.include` | Sem mudança |

---

### Fase 7 — Virtual Threads (padrão no Spring Boot 4)

Spring Boot 4 habilita Virtual Threads por padrão no Tomcat/Jetty embarcado (se Java 21+).

- [ ] Remova `spring.threads.virtual.enabled=true` se estava definido explicitamente (agora é o padrão)
- [ ] Substitua `ThreadLocal` por `ScopedValue` onde há contexto compartilhado (evita leaks com virtual threads)
- [ ] Verifique interceptores e filtros que usam `ThreadLocal` para propagar contexto (MDC, tracing)

```java
// MDC com virtual threads — use o bridge do Micrometer/OTel
// Não use ThreadLocal diretamente em código de negócio novo
```

---

### Fase 8 — Testes e validação

- [ ] `@SpringBootTest` — execute a suíte completa e corrija todos os erros de contexto
- [ ] `@WebMvcTest` — verifique se `MockMvc` ainda funciona com os novos security defaults
- [ ] `@DataJpaTest` — valide queries e compatibilidade com Hibernate 7
- [ ] Testes de integração com Testcontainers — confirme versão das imagens Docker dos bancos
- [ ] Testes de segurança com `@WithMockUser` / `SecurityMockMvcRequestPostProcessors.jwt()`

---

## Output esperado

Para cada fase acima, liste:
1. **Arquivos afetados** no projeto atual
2. **Mudança necessária** — antes × depois em código
3. **Risco** — Baixo | Médio | Alto (com justificativa)
4. **Verificação** — como confirmar que a mudança está correta (teste, log, endpoint)

Ao final, gere uma **lista priorizada** ordenada por risco decrescente, com estimativa de esforço (horas) por item.
