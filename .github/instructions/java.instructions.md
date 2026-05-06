---
description: "Use when writing or reviewing Java code. Covers Java 11/17/21/25 patterns: records, sealed classes, virtual threads, pattern matching, streams, Optional, and framework-agnostic service design."
applyTo: "**/*.java"
---

# Java — Boas Práticas

> Para boas práticas específicas de versão, use a skill `/java-patterns` que carrega
> [java-11](../skills/java-patterns/instructions/java-11.instructions.md),
> [java-17](../skills/java-patterns/instructions/java-17.instructions.md),
> [java-21](../skills/java-patterns/instructions/java-21.instructions.md) ou
> [java-25](../skills/java-patterns/instructions/java-25.instructions.md) conforme a versão do projeto.

## Versão do projeto

Sempre verifique a versão Java antes de sugerir código:
- `pom.xml` → `<java.version>` ou `<maven.compiler.source>`
- `build.gradle` → `sourceCompatibility` ou `java { toolchain { languageVersion } }`
- `.java-version` → arquivo de ferramentas como `jenv` / `sdkman`

## Modern Java (17+)

- Use `record` para imutáveis de dados puros (DTOs, commands, value objects)
- Use `sealed interface` / `sealed class` para hierarquias fechadas e pattern matching exaustivo
- Use pattern matching com `instanceof`: `if (shape instanceof Circle c)` — sem cast explícito
- Use `switch` expressions com `->` para retornos — não `switch` statements com `break`
- Use text blocks `"""..."""` para JSON, SQL, templates — nunca concatenação de Strings

```java
// Records como DTOs imutáveis
public record CreateUserCommand(String name, String email) {}

// Switch expression
String label = switch (status) {
    case ACTIVE   -> "Ativo";
    case INACTIVE -> "Inativo";
    case PENDING  -> "Pendente";
};
```

## Optional

- Use `Optional<T>` apenas em retornos de métodos — nunca como campo de classe ou parâmetro
- Prefira `orElseThrow()`, `map()`, `flatMap()`, `ifPresent()` a `isPresent()` + `get()`
- Nunca retorne `null` de um método que declara `Optional<T>`

```java
// Ruim
Optional<User> opt = repository.findById(id);
if (opt.isPresent()) return opt.get().getName();

// Bom
return repository.findById(id)
    .map(User::getName)
    .orElseThrow(() -> new UserNotFoundException(id));
```

## Streams

- Use streams para transformações funcionais em coleções — não para iterações simples
- Prefira `Collectors.toUnmodifiableList()` a `.collect(toList())` para resultados imutáveis
- Use `Stream.of()`, `Stream.concat()`, `Stream.iterate()` antes de criar coleções intermediárias
- Evite side effects dentro de `.map()` e `.filter()` — use `.forEach()` somente no terminal
- Use `Collectors.groupingBy()`, `partitioningBy()` para agregações

```java
Map<Department, List<Employee>> byDept = employees.stream()
    .filter(Employee::isActive)
    .collect(Collectors.groupingBy(Employee::getDepartment));
```

## Imutabilidade

- Prefira `List.of()`, `Map.of()`, `Set.of()` para coleções imutáveis
- Declare campos como `final` por padrão
- Use `Collections.unmodifiableList()` para expor listas internas sem cópia defensiva
- Nunca exponha coleções mutáveis em getters — retorne uma view ou cópia

## Tratamento de Erros

- Use exceções checked apenas para erros recuperáveis que o chamador deve tratar explicitamente
- Use `RuntimeException` (ou subclasses) para erros de programação e estado inválido
- Crie exceções de domínio específicas (`UserNotFoundException`, `InsufficientFundsException`)
- Nunca capture `Exception` ou `Throwable` genéricos a não ser no topo da stack (handler global)
- Sempre inclua a causa original: `throw new ServiceException("msg", cause)`

## Interfaces e Design

- Prefira interfaces a classes abstratas para definir contratos
- Use `default` methods em interfaces para comportamento compartilhado opcional
- Programe para interfaces: `List<T>`, não `ArrayList<T>` em declarações de campos/parâmetros
- Use `@FunctionalInterface` para interfaces com um único método abstrato

## Injeção de Dependência

- Prefira injeção via construtor a `@Autowired` em campos
- Declare dependências como `final` no construtor
- Evite `ApplicationContext.getBean()` fora de configurações bootstrap

```java
// Bom — injeção via construtor, campo final
public class UserService {
    private final UserRepository repository;
    private final EventPublisher publisher;

    public UserService(UserRepository repository, EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }
}
```

## Convenções de Código

- Siga as [Google Java Style Guidelines](https://google.github.io/styleguide/javaguide.html)
- Use `var` para variáveis locais quando o tipo é óbvio pelo contexto
- Prefira métodos estáticos de fábrica (`User.of(...)`) a construtores públicos com muitos parâmetros
- Use `@NotNull` / `@Nullable` (Jakarta) para documentar contratos de nullability
