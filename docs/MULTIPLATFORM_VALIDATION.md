# Matriz de Validação Multiplataforma (`MULTIPLATFORM_VALIDATION.md`)

Este documento registra o estado real de validação por sistema operacional. **Suporte planejado ou compilação em Linux não equivale a validação em Windows ou macOS.**

**Estados utilizados:** `PASS` · `FAIL` · `PARTIAL` · `BLOCKED` · `NOT_RUN`

---

## Matriz por Plataforma

| Item de Validação | Linux (Fedora `x86_64`) | Linux (Ubuntu/Debian `x86_64`) | Windows (`x86_64`) | macOS (`arm64` / `x86_64`) |
| :--- | :---: | :---: | :---: | :---: |
| Compilação (`mvn clean compile`) | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Testes Unitários & Integração (`mvn clean verify`) | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Carregamento de todas as telas FXML (14 views) | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Inicialização desktop (`mvn javafx:run`) | `PARTIAL` (manual pendente após Fase 6) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Instalação / Desinstalação de pacote nativo | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Descoberta de executáveis (`ExecutableResolver`) | `PASS` | `NOT_RUN` | `NOT_RUN` (testado unitariamente com mock de env) | `NOT_RUN` (testado unitariamente com mock de env) |
| Diretórios de SO (`PlatformDirectories`) | `PASS` | `NOT_RUN` | `PASS` (teste unitário simulado) | `PASS` (teste unitário simulado) |
| Configuração inicial (Assistente 9 etapas) | `PARTIAL` (FXML validado; fluxo manual pendente) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Temas (Claro / Escuro) & i18n (`pt_BR` / `en`) | `PASS` (automatizado) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Conversão real & Operações multimídia (software) | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Aceleração por Hardware (`VAAPI` / GPU) | `PARTIAL` (probe funcional no host detecta corretamente limitação `mesa-va-drivers` sem falso positivo) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Caminhos com espaços / Unicode | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Fila persistente, histórico e recuperação `INTERRUPTED` | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| Cancelamento, limpeza de temporários e encerramento seguro | `PASS` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |

---

## Observações Específicas

1. **Separação Software vs Hardware:** A GPU AMD local do ambiente de desenvolvimento (e a limitação conhecida do `mesa-va-drivers` no host Fedora) **não** é pré-requisito universal; toda a suíte funciona em codificação por software (`libx264`, `aac`, etc.).
2. **Windows e macOS:** Permanecem classificados como **Planejados / Não validados em execução nativa** até que os workflows de CI/Packaging rodem em runners nativos ou máquinas físicas correspondentes.
