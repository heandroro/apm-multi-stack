---
description: "Use when writing or reviewing Java 25 code. Covers LTS features: string templates (GA), unnamed variables and patterns (GA), structured concurrency (GA), scoped values (GA), flexible constructor bodies, module imports, and primitive types in patterns (preview)."
---

# Java 25 — LTS Próxima Geração

Java 25 (setembro 2025) consolida como GA as features que estavam em preview no Java 21/22/23/24. Tudo do Java 21 está disponível + o abaixo.

## String Templates (Java 25 — GA esperado)

Interpolação de strings tipada e extensível — substitui `String.format()` e `formatted()`.

```java
// STR processor (interpolação direta)
String name  = "Alice";
int    age   = 30;
String msg   = STR."Olá, \{name}! Você tem \{age} anos.";

// FMT processor (com formatação)
double price = 49.99;
String label = FMT."Preço: R$ %.2f\{price}";

// RAW processor (para SQL seguro — sem injeção)
PreparedStatement stmt = DB.query(
    RAW."SELECT * FROM users WHERE id = \{userId}"
);
// O template processor pode sanitizar/parametrizar automaticamente
```

Vantagens sobre `formatted()`:
- Verificação de tipos em compile-time
- Processadores customizados (JSON, SQL, HTML) que escapam automaticamente
- Muito mais legível para strings complexas

## Unnamed Variables e Patterns (Java 25 — GA esperado)

Substitui variáveis descartáveis por `_` sem shadow warnings.

```java
// Unnamed variable em catch — quando a exceção não é usada
try {
    return parse(input);
} catch (ParseException _) {
    return defaultValue;
}

// Unnamed em lambda — quando o parâmetro não é usado
list.forEach(_ -> counter.increment());

// Unnamed em enhanced for — quando o índice não importa
for (var _ : collection) {
    count++;
}

// Unnamed pattern em switch — match de tipo sem nome
switch (event) {
    case UserCreated(var id, _)    -> onboard(id);
    case UserDeleted(var id, _, _) -> cleanup(id);
    case _                         -> log("unhandled event");
}
```

## Structured Concurrency (Java 25 — GA esperado)

Sai de preview. A API é a mesma do Java 21, agora estável.

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<User>   user    = scope.fork(() -> findUser(userId));
    Future<Config> config  = scope.fork(() -> loadConfig(userId));

    scope.join();
    scope.throwIfFailed();

    return new UserContext(user.resultNow(), config.resultNow());
}

// ShutdownOnSuccess — retorna o primeiro que completar com sucesso
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Response>()) {
    scope.fork(() -> callPrimaryServer(req));
    scope.fork(() -> callFallbackServer(req));
    scope.join();
    return scope.result();
}
```

## Scoped Values (Java 25 — GA esperado)

Sai de preview. Substitui `ThreadLocal` definitivamente para virtual threads.

```java
static final ScopedValue<RequestContext> REQUEST_CTX = ScopedValue.newInstance();

// Middleware / filter de autenticação
ScopedValue.where(REQUEST_CTX, new RequestContext(userId, traceId)).run(() -> {
    router.handle(request);
});

// Em qualquer camada do call stack — sem passar parâmetros
RequestContext ctx = REQUEST_CTX.get();
String traceId = ctx.traceId();
```

## Flexible Constructor Bodies (Java 25 — GA esperado)

Permite executar código antes de `super()` e `this()` em construtores.

```java
public class ValidatedList<T> extends ArrayList<T> {
    public ValidatedList(List<T> items) {
        // Agora é possível validar ANTES de chamar super()
        if (items == null) throw new IllegalArgumentException("items cannot be null");
        super(items);
    }
}
```

## Module Imports (Java 25 — GA esperado)

Import de todos os pacotes públicos de um módulo com uma linha.

```java
// Antes — import por import
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;

// Java 25 — importa todo o módulo java.base
import module java.base;

// Importa todas as classes públicas do módulo java.sql
import module java.sql;
```

## Primitive Types em Patterns (Java 25 — Preview esperado)

Permite usar tipos primitivos em `instanceof` e `switch` pattern matching.

```java
// instanceof com primitivos
Object obj = 42;
if (obj instanceof int i) {
    System.out.println("É um int: " + i);
}

// switch com primitivos e wrappers unificados
switch (value) {
    case int i when i < 0    -> "negativo";
    case int i when i == 0   -> "zero";
    case int i               -> "positivo";
    case double d            -> "double: " + d;
}
```

## Matriz de features por versão

| Feature | Java 11 | Java 17 | Java 21 | Java 25 |
|---------|---------|---------|---------|---------|
| `var` | GA | GA | GA | GA |
| `record` | — | GA | GA | GA |
| `sealed class` | — | GA | GA | GA |
| Text blocks | — | GA | GA | GA |
| `switch` expression | — | GA | GA | GA |
| Pattern matching `instanceof` | — | GA | GA | GA |
| Record patterns | — | — | GA | GA |
| Switch pattern matching | — | — | GA | GA |
| Sequenced Collections | — | — | GA | GA |
| Virtual Threads | — | — | GA | GA |
| String Templates | — | — | Preview | GA |
| Unnamed variables `_` | — | — | Preview | GA |
| Structured Concurrency | — | — | Preview | GA |
| Scoped Values | — | — | Preview | GA |
| Flexible constructor | — | — | — | GA |
| Module imports | — | — | — | GA |
| Primitive patterns | — | — | — | Preview |

> Nota: Features marcadas como "GA esperado" estão baseadas no roadmap OpenJDK. Verifique [openjdk.org](https://openjdk.org) para o status final.
