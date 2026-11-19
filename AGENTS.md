# FFmpeg Studio — Diretrizes para Agentes de Desenvolvimento

Este documento estabelece as convenções, regras arquiteturais, comandos de automação e critérios de qualidade para agentes de IA e desenvolvedores que colaboram no projeto **FFmpeg Studio**.

---

## 1. Visão Geral do Projeto

**FFmpeg Studio** é uma aplicação desktop desenvolvida em Java 25 e JavaFX que fornece uma interface gráfica moderna, de alta produtividade e local-first para o ecossistema multimídia do FFmpeg e FFprobe.

### Princípios Cardeais
- **Local-first e 100% Offline**: Sem backends remotos, sem telemetria externa, sem dependência de internet.
- **Segurança de Processos**: Nunca executar comandos via shell (`/bin/sh -c`, `cmd.exe`) ou concatenação insegura de strings. Argumentos são sempre transmitidos como listas estruturadas (`List<String>`) para `ProcessBuilder`.
- **Não Bloqueio de Thread de UI**: Toda chamada a executáveis externos ou operações I/O de disco deve ocorrer em threads de background (`AsyncHelper` / `CompletableFuture`) e sincronizar com a interface via `Platform.runLater()`.
- **Preservação de Dados**: Nunca sobrescrever mídias originais sem consentimento explícito e verificação.
- **Zero Emojis na Interface**: Utilizar exclusivamente ícones vetoriais SVG (`SVGPath` / `IconHelper`) com estilização CSS.

---

## 2. Estrutura de Diretórios e Pacotes

O projeto segue estritamente a Arquitetura Hexagonal Simplificada combinada com MVVM para a camada gráfica:

```
ffmpeg-visual/
├── pom.xml
├── .gitignore
├── README.md
├── AGENTS.md
├── docs/
│   ├── ARCHITECTURE.md
│   ├── FEATURES.md
│   ├── FFMPEG_COMMANDS.md
│   └── DEVELOPMENT.md
└── src/
    ├── main/
    │   ├── java/com/ffmpegstudio/
    │   │   ├── domain/               # Domínio Puro (independente de JavaFX/FFmpeg)
    │   │   │   ├── model/            # Records, enums e modelos imutáveis
    │   │   │   └── port/             # Portas (interfaces) de serviço e persistência
    │   │   ├── application/          # Casos de Uso e Orquestração
    │   │   │   ├── port/in/          # Casos de uso de entrada
    │   │   │   └── service/          # Implementações dos serviços de aplicação
    │   │   ├── infrastructure/       # Adaptadores de infraestrutura
    │   │   │   ├── ffmpeg/           # Execução e detecção de FFmpeg/FFprobe
    │   │   │   ├── config/           # Repositório de configurações (JSON)
    │   │   │   └── filesystem/       # Manipulação de arquivos e caminhos
    │   │   └── ui/                   # Camada de Interface (JavaFX + MVVM)
    │   │       ├── FFmpegStudioApp.java # Entrada JavaFX
    │   │       ├── Launcher.java        # Main bootstrap desacoplado
    │   │       ├── AppContext.java      # Composition Root / Injeção de dependência
    │   │       ├── controller/          # FXML Controllers
    │   │       ├── viewmodel/           # ViewModels observáveis
    │   │       └── util/                # AsyncHelper, IconHelper
    │   └── resources/
    │       ├── logback.xml
    │       └── com/ffmpegstudio/
    │           ├── css/
    │           │   └── dark-theme.css
    │           └── views/
    │               ├── main-layout.fxml
    │               ├── dashboard.fxml
    │               └── settings.fxml
    └── test/
        └── java/com/ffmpegstudio/
            └── infrastructure/
                ├── config/
                ├── filesystem/
                └── ffmpeg/
```

---

## 3. Comandos Úteis de Build e Execução

### Compilação
```bash
mvn clean compile
```

### Executar Testes Automatizados
```bash
mvn test
```

### Empacotar JAR
```bash
mvn package -DskipTests
```

### Executar a Aplicação JavaFX (Desktop)
```bash
mvn javafx:run
```
Ou executando diretamente o JAR compilado com os módulos JavaFX:
```bash
java --module-path target/classes:$HOME/.m2/repository/org/openjfx/... \
     -m com.ffmpegstudio/com.ffmpegstudio.ui.Launcher
```

---

## 4. Convenções de Código

1. **Java 25 Moderno**:
   - Utilize `record` para DTOs, valores imutáveis e configurações.
   - Utilize pattern matching para `switch` e `instanceof`.
   - Utilize métodos de fábrica imutáveis (`List.of()`, `Map.of()`).
2. **Separação de Camadas**:
   - O pacote `domain` **nunca** importa `javafx.*`, `java.awt.*` ou classes de `infrastructure`.
   - Controllers FXML apenas delegam eventos e observam propriedades expostas pelos `ViewModels`.
   - Processos FFmpeg só podem ser invocados através de `ProcessExecutorPort` implementado na camada de infraestrutura.
3. **Tratamento de I/O e Exceções**:
   - Sempre utilize caminhos seguros (`java.nio.file.Path`).
   - Todos os streams de processos externos (`stdout` e `stderr`) devem ser consumidos em threads assíncronas para evitar esgotamento de buffers do kernel do sistema operacional.
4. **Testes**:
   - Prefira *Test Doubles* e *Fakes* limpos para testes unitários em vez de manipulação profunda de bytecode / reflection no Java 25.
   - Testes de integração devem usar `@EnabledIf` ou `Assumptions.assumeTrue(...)` para verificar se ferramentas locais (FFmpeg, FFprobe) estão presentes antes de executar validações de sistema real.

---

## 5. Critérios de Conclusão para Novas Tarefas

Para que uma fase ou funcionalidade seja considerada pronta:
1. `mvn compile` compila sem erros ou alertas graves.
2. `mvn test` executa com 100% de sucesso.
3. A funcionalidade visual (FXML/CSS) possui layout responsivo e aderente ao tema escuro.
4. Toda execução de linha de comando é estruturada em lista de argumentos.
5. Os documentos relevantes em `docs/` são atualizados.
