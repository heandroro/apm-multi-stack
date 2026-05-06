---
description: "Use when writing async code with Kotlin Coroutines or Flow. Covers structured concurrency, coroutine scopes, Flow operators, StateFlow, SharedFlow, and error handling in async contexts."
---

# Kotlin Coroutines e Flow — Padrões

## Structured Concurrency

- Nunca use `GlobalScope` — cria coroutines não rastreáveis e não canceláveis
- Use `coroutineScope { }` para lançar coroutines filhas — cancela todas se uma falha
- Use `supervisorScope { }` quando uma falha de filho não deve cancelar os outros
- O `CoroutineScope` deve ter o mesmo ciclo de vida do componente que o criou

```kotlin
// Bom — structured, cancelável
suspend fun processAll(items: List<Item>): List<Result> = coroutineScope {
    items.map { item -> async { process(item) } }.awaitAll()
}
```

## Dispatchers

| Dispatcher | Quando usar |
|------------|-------------|
| `Dispatchers.IO` | I/O bloqueante (DB, filesystem, HTTP síncrono) |
| `Dispatchers.Default` | Computação intensiva (CPU-bound) |
| `Dispatchers.Main` | UI (Android) |
| `Dispatchers.Unconfined` | Raramente — debugging/testes apenas |

```kotlin
suspend fun fetchFromDatabase(id: String): User = withContext(Dispatchers.IO) {
    blockingRepository.findById(id) ?: throw UserNotFoundException(id)
}
```

## Cancelamento e Lifecycle

- Funções `suspend` verificam cancelamento automaticamente em pontos de suspensão
- Para código não-suspending, verifique `isActive` ou chame `ensureActive()`
- Sempre use `try/finally` ou `use {}` para liberar recursos, mesmo após cancelamento
- Nunca capture `CancellationException` sem re-throw

```kotlin
suspend fun longOperation(): Result {
    return try {
        repeat(1000) {
            ensureActive()  // verifica cancelamento em loops longos
            doWork(it)
        }
        Result.Success
    } finally {
        cleanup()  // sempre executado, mesmo se cancelado
    }
}
```

## Flow — Streams Reativos

- Use `Flow<T>` para sequências assíncronas (cold stream — executa ao coletar)
- Use `callbackFlow { }` para converter callbacks em Flow
- Use `stateIn()` / `shareIn()` para converter cold em hot Flow com sharing
- Combine múltiplos flows com `combine()`, `zip()`, `merge()`

```kotlin
// Cold flow — executa apenas quando coletado
fun userUpdates(userId: String): Flow<User> = flow {
    while (true) {
        emit(repository.findById(userId))
        delay(5_000)
    }
}

// Operadores
userUpdates(userId)
    .distinctUntilChanged()
    .filter { it.isActive }
    .map { it.toResponse() }
    .catch { e -> emit(UserResponse.error(e.message)) }
    .collect { response -> send(response) }
```

## StateFlow e SharedFlow

```kotlin
// StateFlow — estado atual sempre disponível, replay = 1
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// SharedFlow — eventos broadcast sem replay por padrão
private val _events = MutableSharedFlow<Event>()
val events: SharedFlow<Event> = _events.asSharedFlow()

// Emitir sem suspender (cuidado: descarta se buffer cheio)
fun emitEvent(event: Event) { _events.tryEmit(event) }
```

## Exception Handling em Coroutines

```kotlin
// CoroutineExceptionHandler para coroutines de top-level (launch)
val handler = CoroutineExceptionHandler { _, exception ->
    logger.error("Unhandled exception in coroutine", exception)
}

scope.launch(handler) {
    riskyOperation()
}

// async — exceção é relançada no await()
val deferred = scope.async {
    riskyOperation()
}
try {
    deferred.await()
} catch (e: ServiceException) {
    handleError(e)
}
```

## Integração com Spring

- Use `@EnableCoroutines` em projetos Spring WebFlux
- Declare funções de controller e service como `suspend fun`
- Use `CoroutineScope(SupervisorJob() + Dispatchers.IO)` para scopes de longa duração
- Destrua o scope com `scope.cancel()` no `@PreDestroy`
