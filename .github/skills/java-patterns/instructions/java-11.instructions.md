---
description: "Use when writing or reviewing Java 11 code. Covers LTS baseline: var, HttpClient, String API improvements, Optional enhancements, collection factories, and stream idioms compatible with Java 11."
---

# Java 11 — LTS Baseline

Java 11 (setembro 2018) é o mínimo LTS recomendado. Todas as features abaixo são **GA** nesta versão.

## var — Local Variable Type Inference

```java
// Bom — tipo óbvio pelo lado direito
var users = new ArrayList<User>();
var client = HttpClient.newHttpClient();

// Ruim — tipo não óbvio, perde legibilidade
var x = process();
```

- Use `var` somente quando o tipo é **óbvio** na mesma linha
- Nunca em campos de classe, parâmetros ou retornos de método
- Nunca com `null` como inicializador

## String API (Java 11)

```java
// isBlank() — unicode-aware (melhor que isEmpty())
if (!name.isBlank()) { ... }

// strip() — unicode-aware (melhor que trim())
String clean = "  hello  ".strip();
String left  = "  hello  ".stripLeading();
String right = "  hello  ".stripTrailing();

// lines() — stream de linhas
long count = text.lines().filter(l -> !l.isBlank()).count();

// repeat()
String separator = "-".repeat(40);
```

## HttpClient (Java 11 — GA)

Substitui `HttpURLConnection` para chamadas HTTP sem dependências externas.

```java
var client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Authorization", "Bearer " + token)
    .timeout(Duration.ofSeconds(30))
    .GET()
    .build();

// Síncrono
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

// Assíncrono com CompletableFuture
client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    .thenApply(HttpResponse::body)
    .thenAccept(body -> process(body));
```

## Optional — melhorias Java 9–11

```java
// ifPresentOrElse (Java 9)
optional.ifPresentOrElse(user -> process(user), () -> log.warn("not found"));

// or (Java 9) — fallback para outro Optional
Optional<User> result = primary.or(() -> secondary.findByEmail(email));

// stream (Java 9) — integra Optional em pipeline Stream
List<User> active = optionals.stream()
    .flatMap(Optional::stream)
    .filter(User::isActive)
    .collect(Collectors.toUnmodifiableList());
```

## Collection Factories (Java 9+)

```java
// Imutáveis — prefira sempre quando a coleção não precisar ser modificada
List<String> roles = List.of("ADMIN", "USER", "GUEST");
Set<String>  tags  = Set.of("java", "backend", "api");
Map<String, Integer> codes = Map.of("OK", 200, "NOT_FOUND", 404);

// Map.ofEntries para mais de 10 entradas
Map<String, Integer> large = Map.ofEntries(
    Map.entry("OK", 200),
    Map.entry("CREATED", 201),
    Map.entry("NO_CONTENT", 204),
    Map.entry("NOT_FOUND", 404)
);
```

## Files API (Java 11)

```java
// Ler/escrever String diretamente — sem BufferedReader/Writer
String content = Files.readString(Path.of("config.json"));
Files.writeString(Path.of("output.txt"), result, StandardCharsets.UTF_8);
```

## O que NÃO está disponível no Java 11

| Feature | Disponível em | Alternativa em Java 11 |
|---------|--------------|------------------------|
| `record` | Java 16 GA | Classe com campos `final` + construtor + equals/hashCode manual |
| `sealed class` | Java 17 GA | Hierarquia fechada por convenção |
| Text blocks `"""` | Java 15 GA | `String.join("\n", ...)` |
| `switch` expression | Java 14 GA | `switch` statement com variável local |
| Pattern matching `instanceof` | Java 16 GA | Cast explícito após `instanceof` |
| `Stream.toList()` | Java 16 | `.collect(Collectors.toUnmodifiableList())` |
| Virtual Threads | Java 21 GA | `ExecutorService` com pool de threads |
