# APM Multi-skill Package — Contexto Global

Este workspace é um pacote de customização do GitHub Copilot especializado em **Kotlin**, **Java**, **Go** e **Python**.

## O que você encontra aqui

- **Skills** com boas práticas, templates e sub-agents por linguagem/framework (`/kotlin-patterns`, `/java-patterns`, `/spring-patterns`, `/quarkus-patterns`, `/go-patterns`, `/python-patterns`)
- **Instructions** aplicadas automaticamente a arquivos `.kt`, `.java`, `.go`, `.py`
- **Agents** especializados por linguagem + orquestrador multi-stack
- **MCP GitHub** para referenciar templates em repositórios externos

## Frameworks cobertos

| Linguagem / Domínio | Frameworks / Skill |
|---------------------|--------------------|
| Kotlin | Spring Boot, Ktor, Coroutines/Flow |
| Java (idioms + versões) | Java 11/17/21/25, Micronaut, JUnit 5 → `/java-patterns` |
| Quarkus | RESTEasy Reactive, Panache, CDI, SmallRye, GraalVM native → `/quarkus-patterns` |
| Spring Framework | Spring Boot 3.x/4.x, Spring Cloud AWS 3.x, Spring Security → `/spring-patterns` |
| Go | Gin, Fiber, net/http, gRPC/protobuf |
| Python | FastAPI, Django, Flask + Pydantic |

## Convenções deste pacote

- Prefira **idiomatic code** de cada linguagem — não escreva Java em Kotlin, nem Python em Go
- Toda sugestão de código deve vir acompanhada de tratamento de erro adequado para a linguagem
- Use os tipos e abstrações nativos antes de introduzir dependências externas
- Docstrings/KDoc/GoDoc/docstrings seguem os padrões idiomáticos de cada linguagem

## Como navegar

- Para boas práticas gerais de uma linguagem → skills (`/kotlin-patterns`, `/java-patterns`, etc.)
- Para Spring Boot / Spring Cloud AWS → skill `/spring-patterns`
- Para Quarkus / SmallRye / GraalVM native → skill `/quarkus-patterns`
- Para scaffold rápido → prompts dentro das skills (`scaffold-service`, `scaffold-spring-service`, `scaffold-quarkus-service`, `scaffold-handler`)
- Para revisão focada de um framework → agents especializados (`kotlin-expert`, `spring-specialist`, `quarkus-specialist`, etc.)
- Para buscar templates de repositórios externos → use qualquer agent com o GitHub MCP disponível

## Segurança

- Nunca exponha segredos, tokens ou credenciais no código
- Valide inputs em fronteiras do sistema (HTTP handlers, CLI args, env vars)
- Prefira autenticação por variáveis de ambiente a hardcoded credentials
- Siga as diretrizes OWASP Top 10 para APIs e serviços web
