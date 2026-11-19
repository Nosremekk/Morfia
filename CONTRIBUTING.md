# Contribuindo com o Projeto

Obrigado pelo interesse em contribuir! Este projeto é local-first, 100% offline e construído com **Java 25**, **JavaFX 24** e **Maven**.

---

## 1. Arquitetura e Regras Obrigatórias

O código segue **Arquitetura Hexagonal Simplificada + MVVM** (consulte [AGENTS.md](AGENTS.md) e [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):

- **`domain`**: Regras, modelos imutáveis (`record`, `enum`), validadores e portas. **Nunca** deve importar `javafx.*`, `java.awt.*` ou classes de `infrastructure`.
- **`application`**: Casos de uso e serviços de orquestração (`TranscodeMediaService`, `JobQueueService`, `MediaLibraryService`, etc.).
- **`infrastructure`**: Adaptadores para FFmpeg/FFprobe (`FFmpegProcessExecutor`, `FFmpegCommandBuilder`), persistência JSON (`Json*Repository`) e abstração de sistema operacional (`infrastructure/platform`).
- **`ui`**: Camada JavaFX (FXML, CSS, ViewModels e Controllers). Controllers não devem construir comandos FFmpeg diretamente.

### Regras de Segurança e Qualidade
1. **Nunca execute comandos via shell** (`/bin/sh -c`, `cmd.exe`, concatenação de strings). Use sempre `ProcessBuilder` com `List<String>` através de `ProcessExecutorPort`.
2. **Consuma `stdout` e `stderr` de forma assíncrona** para evitar deadlocks de buffer e nunca bloqueie a JavaFX Application Thread.
3. **Sem desserialização polimórfica irrestrita** no Jackson (`enableDefaultTyping` é proibido).
4. **Zero emojis na interface gráfica**: use apenas ícones vetoriais SVG em `IconHelper`.
5. **Não sobrescreva mídias originais** sem confirmação explícita.

---

## 2. Build e Testes Locais

### Pré-requisitos
- JDK 25+
- Maven 3.9+
- FFmpeg e FFprobe instalados no sistema para os testes de integração (testes de integração usam `Assumptions` caso não estejam presentes)

### Comandos
```bash
# Compilar
mvn clean compile

# Executar toda a suíte (unitários, integração e carregamento FXML)
mvn clean verify

# Executar a aplicação desktop
mvn javafx:run
```

---

## 3. Convenção de Commits e Pull Requests

Adotamos mensagens no estilo **Conventional Commits**:
- `feat: ...` — Nova funcionalidade
- `fix: ...` — Correção de bug
- `docs: ...` — Alterações de documentação
- `refactor: ...` — Refatoração sem mudança de comportamento
- `test: ...` — Adição ou ajuste de testes
- `build: ...` / `ci: ...` — Empacotamento, Maven ou workflows

Antes de abrir um Pull Request:
1. Garanta que `mvn clean verify` passa sem falhas.
2. Atualize a documentação técnica relevante em `docs/` se alterar fluxos, configurações ou comandos.
3. Preencha o template de Pull Request descrevendo o escopo e os testes executados.

---

## 4. Etiquetas Sugeridas (Issues e PRs)

`bug` · `enhancement` · `documentation` · `good first issue` · `help wanted` · `linux` · `windows` · `macos` · `packaging` · `compatibility` · `security`
