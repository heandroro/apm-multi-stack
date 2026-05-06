---
name: review-coroutines
description: "Review Kotlin coroutines code for correctness, structured concurrency violations, cancellation leaks, dispatcher misuse, and Flow anti-patterns."
---

Revise o seguinte código Kotlin com foco em **Coroutines e Flow**:

```kotlin
$code
```

## Checklist de revisão

### Structured Concurrency
- [ ] Nenhum uso de `GlobalScope`?
- [ ] `coroutineScope {}` ou `supervisorScope {}` para concorrência estruturada?
- [ ] Todos os `async {}` têm `.await()` chamado?

### Cancelamento
- [ ] `CancellationException` nunca é capturada e descartada?
- [ ] Loops longos verificam `ensureActive()` ou têm pontos de suspensão?
- [ ] Recursos são liberados em `finally {}` ou com `use {}`?

### Dispatchers
- [ ] `Dispatchers.IO` para I/O bloqueante?
- [ ] `Dispatchers.Default` para computação intensiva?
- [ ] `GlobalScope` com `Dispatchers.Unconfined` nunca usado?

### Flow
- [ ] `collect {}` não bloqueia coroutines pai?
- [ ] `catch {}` está presente para flows que podem falhar?
- [ ] `stateIn()` / `shareIn()` usado para evitar reexecução em múltiplos coletores?
- [ ] Side effects apenas em `onEach {}` ou terminal operators?

### Exception Handling
- [ ] `launch {}` tem `CoroutineExceptionHandler`?
- [ ] `async {}` tem `.await()` dentro de try/catch?

## Output

Para cada problema encontrado:
1. **Localização**: linha ou função
2. **Problema**: descrição do issue
3. **Impacto**: vazamento de memória | deadlock | comportamento incorreto | crash
4. **Correção sugerida**: código corrigido
