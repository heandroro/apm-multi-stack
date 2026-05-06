---
description: "Use when writing or reviewing Kotlin code. Covers null safety, coroutines, data classes, sealed classes, extension functions, Spring Boot patterns, and Ktor routes."
applyTo: "**/*.kt"
---

# Kotlin — Boas Práticas

## Null Safety

- Prefira tipos não-nulos por padrão. Use `?` somente quando `null` tem semântica real
- Use `?.let`, `?.also`, `?:` em vez de `if (x != null)` explícito
- Evite `!!` — substitua por `?: error("mensagem descritiva")` ou `requireNotNull()`
- Use `lateinit var` apenas para injeção de dependência onde null safety não se aplica

```kotlin
// Ruim
val name: String? = user?.name
if (name != null) println(name.uppercase())

// Bom
user?.name?.let { println(it.uppercase()) }
```

## Data Classes e Value Objects

- Use `data class` para objetos que representam dados puros (DTOs, commands, events)
- Prefira `value class` (inline class) para tipos primitivos com semântica de domínio
- Não coloque lógica de negócio em `data class` — use classes separadas ou extension functions

```kotlin
@JvmInline
value class UserId(val value: UUID)

data class CreateUserCommand(val name: String, val email: String)
```

## Sealed Classes e When

- Use `sealed class` / `sealed interface` para modelar resultados e estados
- Sempre cubra todos os casos no `when` — o compilador garante exaustividade
- Prefira `sealed interface` para hierarquias abertas a extensão

```kotlin
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val error: Throwable) : Result<Nothing>
}

fun handle(result: Result<User>) = when (result) {
    is Result.Success -> renderUser(result.data)
    is Result.Failure -> renderError(result.error)
}
```

## Extension Functions

- Use extension functions para adicionar comportamento sem herança
- Prefira extension functions a funções utilitárias estáticas
- Não use extension functions para esconder lógica de negócio — prefira métodos de classe

```kotlin
fun String.toSlug(): String = lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")

fun LocalDate.isWeekend(): Boolean = dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
```

## Coroutines e Flow

- Use `suspend fun` para operações assíncronas em vez de callbacks ou RxJava
- Use `Flow<T>` para streams de dados reativos (nunca `List<T>` assíncrono)
- Propagate o `CoroutineContext` via parâmetros — nunca use `GlobalScope`
- Prefira `coroutineScope { }` para concorrência estruturada
- Use `StateFlow` / `SharedFlow` para estado reativo compartilhado

```kotlin
// Ruim — GlobalScope vaza coroutines
fun fetchUser(id: UserId) = GlobalScope.launch { ... }

// Bom — estruturado e cancelável
suspend fun fetchUser(id: UserId): User = withContext(Dispatchers.IO) {
    repository.findById(id) ?: error("User $id not found")
}
```

## Tratamento de Erros

- Use tipos de resultado (`Result<T>`, `sealed class`) no domínio — não exceções para fluxo de controle
- Reserve exceções para erros verdadeiramente excepcionais (infra, configuração)
- Use `runCatching { }` para converter exceções de libs externas em `Result<T>`
- Em coroutines, lide com `CancellationException` — nunca capture e descarte
- Use `CoroutineExceptionHandler` para erros não-tratados em coroutines de background

## Collections e Functional Style

- Prefira `map`, `filter`, `fold`, `groupBy` a loops imperativos
- Use `asSequence()` para pipelines longas com coleções grandes (lazy evaluation)
- Use `buildList { }`, `buildMap { }` para construtores mutáveis com resultado imutável

## Convenções de Código

- Funções com retorno simples: use sintaxe de expressão `fun x() = ...`
- Prefira `val` a `var` sempre que possível
- Use named arguments em chamadas com 3+ parâmetros do mesmo tipo
- Evite `companion object` apenas para constantes — use `const val` no top-level
