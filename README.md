# APM Multi-skill Package

**Agent Package Manager** — pacote de customização do GitHub Copilot especializado em Kotlin, Java, Go e Python. Fornece boas práticas, templates e agentes especializados carregados on-demand.

## Como funciona

Este repositório é um pacote de primitivas do Copilot. Ao abrir o workspace no VS Code com GitHub Copilot habilitado:

- **Instructions** são aplicadas automaticamente ao editar arquivos `.kt`, `.java`, `.go`, `.py`
- **Skills** são invocadas via `/` no chat (ex: `/kotlin-patterns`)
- **Agents** são selecionados no seletor de agentes ou delegados automaticamente
- **MCP GitHub** permite referenciar templates de código em repositórios externos

## Estrutura

```
.github/
├── copilot-instructions.md       ← contexto global do pacote
├── agents/                       ← agentes especializados + orquestrador
├── instructions/                 ← boas práticas por linguagem (auto-attach)
└── skills/                       ← skills com instructions, prompts e agents internos
    ├── kotlin-patterns/
    ├── java-patterns/
    ├── go-patterns/
    └── python-patterns/
.vscode/
└── mcp.json                      ← GitHub MCP server (referência a repos externos)
```

## Linguagens e Frameworks

| Linguagem | Frameworks cobertos |
|-----------|---------------------|
| **Kotlin** | Spring Boot, Ktor, Coroutines/Flow |
| **Java** | Spring Boot, Quarkus, Micronaut |
| **Go** | Gin, Fiber, net/http, gRPC/protobuf |
| **Python** | FastAPI, Django, Flask + Pydantic |

## Setup

### 1. Pré-requisitos

- VS Code com extensão **GitHub Copilot** e **GitHub Copilot Chat** instaladas
- Docker (para o GitHub MCP server) — ou Node.js 18+ como alternativa

### 2. Configurar o GitHub Token (para MCP)

```bash
export GITHUB_TOKEN=ghp_seu_token_aqui
```

Ou adicione ao seu `.zshrc` / `.bashrc`:
```bash
echo 'export GITHUB_TOKEN=ghp_seu_token_aqui' >> ~/.zshrc
```

O token precisa de permissão `repo:read` para acessar repositórios de templates.

### 3. Habilitar o MCP no VS Code

No `settings.json` do VS Code, certifique-se de que MCP está habilitado:
```json
{
  "github.copilot.chat.experimental.mcp": true
}
```

O arquivo `.vscode/mcp.json` já está configurado neste repositório.

## Uso

### Skills (via chat)

```
/kotlin-patterns  → patterns e templates Kotlin
/java-patterns    → patterns e templates Java
/go-patterns      → patterns e templates Go
/python-patterns  → patterns e templates Python
```

### Agents (via seletor no chat)

| Agent | Quando usar |
|-------|-------------|
| `multi-stack-expert` | Não sabe qual linguagem usar, ou precisa de visão comparativa |
| `kotlin-expert` | Código Kotlin: Spring Boot, Ktor, coroutines |
| `java-expert` | Código Java: Spring Boot, Quarkus, Micronaut |
| `go-expert` | Código Go: Gin, Fiber, gRPC |
| `python-expert` | Código Python: FastAPI, Django, Flask |

### Instructions automáticas

Ao abrir/editar um arquivo, as instructions são carregadas automaticamente:

```
*.kt   → kotlin.instructions.md   (null safety, coroutines, idiomatic Kotlin)
*.java → java.instructions.md     (records, streams, Optional, modern Java)
*.go   → go.instructions.md       (error handling, interfaces, goroutines)
*.py   → python.instructions.md   (type hints, Pydantic, async patterns)
```

### Referenciar templates via MCP GitHub

Com o MCP configurado, os agents podem buscar templates diretamente de repositórios:

```
"Busque o template de serviço gRPC em github.com/minha-org/go-templates"
"Use o padrão de repositório do repo kotlin-templates como base"
```

## Conectar repositórios de templates

Para adicionar repositórios de templates, edite `.vscode/mcp.json` e os arquivos de skill:

1. Configure o `GITHUB_TOKEN` com acesso ao repo de templates
2. Nos prompts das skills (ex: `skills/go-patterns/prompts/scaffold-handler.prompt.md`), referencie o repositório:
   ```
   Busque o arquivo `templates/gin-handler.go` em {owner}/{repo} usando o GitHub MCP
   ```
3. O agent irá usar `github/get_file_contents` para recuperar o template

## Contribuindo

- **Adicionar framework**: crie `*.instructions.md` na pasta `instructions/` da skill correspondente
- **Adicionar template**: adicione em `assets/` e referencie no `SKILL.md`
- **Adicionar prompt**: crie `*.prompt.md` na pasta `prompts/` da skill
- **Novo sub-specialist**: crie `*.agent.md` na pasta `agents/` da skill
