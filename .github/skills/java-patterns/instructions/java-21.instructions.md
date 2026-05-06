---
description: "Use when writing or reviewing Java 21 code. Covers LTS features: virtual threads (Project Loom), record patterns, pattern matching for switch, sequenced collections, structured concurrency (preview), and scoped values (preview)."
---

# Java 21 — LTS Virtual Threads e Pattern Matching

Java 21 (setembro 2023) é a versão LTS com o maior salto de runtime desde o Java 8. Tudo do Java 17 está disponível + o abaixo.

## Virtual Threads — Project Loom (Java 21 GA)

Threads leves gerenciadas pela JVM. Permitem escrever código **bloqueante** com escalabilidade de código reativo — sem callbacks, sem `CompletableFuture`, sem frameworks reativos.

```java
// Thread virtual simples
Thread vThread = Thread.ofVirtual().start(() -> {
    var result = blockingDatabaseCall();  // bloqueia sem consumir thread do SO
    process(result);
});

// ExecutorService com virtual threads — ideal para servidores HTTP
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handleRequest(req1));
    executor.submit(() -> handleRequest(req2));
    // aguarda ambos ao fechar o try-with-resources
}

// Spring Boot 3.2+ — habilitar virtual threads globalmente
// application.properties:
// spring.threads.virtual.enabled=true
```

Quando usar Virtual Threads:
- Servidores HTTP com muitas conexões simultâneas
- Chamadas a banco de dados bloqueantes
- Processamento de filas e mensageria
- **NÃO** use para tarefas CPU-bound intensivas — use `ForkJoinPool` ou `Dispatchers.Default`

## Record Patterns (Java 21 GA)

Desestrutura records diretamente no `instanceof` e `switch`.

```java
public record Point(int x, int y) {}
public record Line(Point start, Point end) {}

// Desestruturação em instanceof
if (shape instanceof Circle(var radius)) {
    return Math.PI * radius * radius;
}

// Desestruturação em switch com sealed
double area = switch (shape) {
    case Circle(var r)            -> Math.PI * r * r;
    case Rectangle(var w, var h)  -> w * h;
    case Triangle(var b, var h)   -> 0.5 * b * h;
};

// Desestruturação aninhada
if (event instanceof OrderPlaced(var order, OrderItem(var productId, var qty))) {
    process(productId, qty);
}
```

## Pattern Matching para Switch (Java 21 GA)

```java
// Matching de tipos em switch — substitui instanceof chain
String format(Object obj) {
    return switch (obj) {
        case Integer i -> "int: " + i;
        case Long l    -> "long: " + l;
        case Double d  -> "double: " + d;
        case String s  -> "string: " + s;
        case null      -> "null";
        default        -> "other: " + obj.getClass().getName()
    };
}

// Com guarda (when)
String classify(Number n) {
    return switch (n) {
        case Integer i when i < 0  -> "negative int";
        case Integer i when i == 0 -> "zero";
        case Integer i             -> "positive int";
        case Double d when d.isNaN() -> "NaN";
        default                    -> "other number";
    };
}
```

## Sequenced Collections (Java 21 GA)

Nova hierarquia de interfaces para coleções com ordem definida.

```java
// SequencedCollection — acesso ao primeiro e último elemento
List<String> list = new ArrayList<>(List.of("a", "b", "c"));

String first = list.getFirst();  // "a"  — antes: list.get(0)
String last  = list.getLast();   // "c"  — antes: list.get(list.size() - 1)

list.addFirst("z");  // ["z", "a", "b", "c"]
list.addLast("d");   // ["z", "a", "b", "c", "d"]
list.removeFirst();
list.removeLast();

// reversed() — view invertida sem cópia
List<String> reversed = list.reversed();

// SequencedMap
LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
map.put("a", 1);
map.put("b", 2);
Map.Entry<String, Integer> first = map.firstEntry();  // {a=1}
Map.Entry<String, Integer> last  = map.lastEntry();   // {b=2}
```

## Structured Concurrency (Java 21 — Preview)

Trata grupos de subtarefas como uma unidade — cancela o grupo se qualquer tarefa falhar.

```java
// Habilitar: --enable-preview
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<User>    user    = scope.fork(() -> findUser(userId));
    Future<Account> account = scope.fork(() -> findAccount(userId));

    scope.join();           // aguarda ambos
    scope.throwIfFailed();  // propaga exceção se algum falhou

    return new UserProfile(user.resultNow(), account.resultNow());
}
```

## Scoped Values (Java 21 — Preview)

Alternativa ao `ThreadLocal` para virtual threads — imutável e com escopo delimitado.

```java
// Habilitar: --enable-preview
static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

// Definir valor para o escopo de um bloco
ScopedValue.where(CURRENT_USER, authenticatedUser).run(() -> {
    processOrder(orderId);  // pode ler CURRENT_USER sem parâmetro
});

// Ler em qualquer ponto do call stack dentro do escopo
User user = CURRENT_USER.get();  // nunca null — erro se fora do escopo
```

## Checklist Java 21

- [ ] Virtual threads para I/O bloqueante em vez de reactive frameworks
- [ ] `Thread.ofVirtual()` ou `Executors.newVirtualThreadPerTaskExecutor()`
- [ ] Record patterns para desestruturação em `switch`
- [ ] `getFirst()` / `getLast()` em vez de `get(0)` / `get(size-1)`
- [ ] `switch` com `when` (guarda) para lógica condicional dentro do case
- [ ] **Verificar flag `--enable-preview`** para Structured Concurrency e Scoped Values

## Compatibilidade de frameworks

| Framework | Java 21 suportado? | Virtual Threads automático? |
|-----------|--------------------|-----------------------------|
| Spring Boot 3.2+ | Sim | Sim — `spring.threads.virtual.enabled=true` |
| Quarkus 3.3+ | Sim | Sim — configurável |
| Micronaut 4.2+ | Sim | Sim — configurável |
