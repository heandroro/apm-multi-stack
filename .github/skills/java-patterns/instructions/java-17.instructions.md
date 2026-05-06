---
description: "Use when writing or reviewing Java 17 code. Covers LTS features: records, sealed classes, pattern matching instanceof, text blocks, switch expressions, and Stream.toList()."
---

# Java 17 — LTS Modern Java

Java 17 (setembro 2021) é a versão LTS que introduz as features modernas mais importantes. Tudo do Java 11 está disponível + o abaixo.

## Records (Java 16 GA)

Substitui classes de dados imutáveis com boilerplate zero.

```java
// Record como DTO imutável — equals, hashCode, toString gerados
public record CreateUserRequest(String name, String email) {}

public record UserResponse(UUID id, String name, String email) {
    // Factory method para converter de entidade
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}

// Record com validação no construtor canônico compacto
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount cannot be negative");
        Objects.requireNonNull(currency);
    }
}
```

- Records são implicitamente `final` — não podem ser estendidos
- Todos os campos são `final` e `private` — imutáveis por padrão
- Podem implementar interfaces

## Sealed Classes (Java 17 GA)

Hierarquias fechadas com exaustividade garantida pelo compilador.

```java
public sealed interface Shape permits Circle, Rectangle, Triangle {}

public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}
public record Triangle(double base, double height) implements Shape {}

// Compilador garante exaustividade no switch/if
double area = switch (shape) {
    case Circle c       -> Math.PI * c.radius() * c.radius();
    case Rectangle r    -> r.width() * r.height();
    case Triangle t     -> 0.5 * t.base() * t.height();
    // sem default necessário — compilador verifica exaustividade
};
```

Use `sealed` para:
- Resultados de operações (`Success | Failure | Pending`)
- Hierarquias de domínio fechadas (`PaymentMethod`: `CreditCard | Pix | Boleto`)
- ADTs (Algebraic Data Types) em Java

## Pattern Matching com instanceof (Java 16 GA)

```java
// Antes do Java 16
if (obj instanceof String) {
    String s = (String) obj;
    return s.toUpperCase();
}

// Java 16+ — pattern variable no mesmo if
if (obj instanceof String s) {
    return s.toUpperCase();
}

// Com guarda (Java 21+) — antecipação de como usar
if (obj instanceof String s && s.length() > 5) {
    return s.substring(0, 5);
}
```

## Text Blocks (Java 15 GA)

```java
// JSON, SQL, HTML sem concatenação
String json = """
        {
            "name": "%s",
            "email": "%s"
        }
        """.formatted(name, email);

String sql = """
        SELECT u.id, u.name, u.email
        FROM users u
        WHERE u.active = true
          AND u.created_at > :since
        ORDER BY u.name
        """;
```

## Switch Expressions (Java 14 GA)

```java
// Switch como expressão — retorna valor, sem fall-through
String label = switch (status) {
    case ACTIVE   -> "Ativo";
    case INACTIVE -> "Inativo";
    case PENDING  -> "Aguardando";
};

// Com bloco e yield quando necessário
int score = switch (grade) {
    case "A" -> 10;
    case "B" -> 8;
    case "C" -> 6;
    default  -> {
        log.warn("Unknown grade: {}", grade);
        yield 0;
    }
};
```

## Stream.toList() (Java 16)

```java
// Antes
List<User> active = users.stream()
    .filter(User::isActive)
    .collect(Collectors.toUnmodifiableList());

// Java 16+ — equivalente, imutável
List<User> active = users.stream()
    .filter(User::isActive)
    .toList();
```

## Checklist Java 17

- [ ] DTOs como `record` — sem classes POJO com getters/setters
- [ ] `sealed interface` para hierarquias fechadas de domínio
- [ ] `switch` expression em vez de `switch` statement com variável
- [ ] Text blocks para SQL, JSON, XML multi-linha
- [ ] `.toList()` em vez de `.collect(toUnmodifiableList())`
- [ ] Pattern matching `instanceof` — sem cast explícito

## Compatibilidade de frameworks

| Framework | Java 17 suportado? |
|-----------|--------------------|
| Spring Boot 3.x | Sim — requerido mínimo |
| Spring Boot 2.7.x | Sim |
| Quarkus 3.x | Sim |
| Micronaut 4.x | Sim |
