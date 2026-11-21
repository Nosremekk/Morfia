# Matriz de Validação Multiplataforma — Morfia `1.0.0-rc.1`

**Estados:** `PASS` · `FAIL` · `PARTIAL` · `BLOCKED` · `NOT_RUN` · `NOT_APPLICABLE`

| Critério | Linux Fedora (`x86_64`) | Linux Ubuntu 24.04 (`x86_64` CI) | Windows (`x86_64` CI) | macOS (`arm64` CI) | macOS (`x86_64`) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Código compilado** | `PASS` | `PASS` (`44933069499`) | `PASS` (`44933069499`) | `PASS` (`44933069499`) | `NOT_RUN` |
| **Testes executados (106 testes / 14 FXML)** | `PASS` | `PASS` (Xvfb) | `PASS` (106/106) | `PASS` (106/106) | `NOT_RUN` |
| **Pacote construído** | `PASS` (`app-image`, `.tar.gz`, `.rpm`, `.deb`) | `PASS` (`.tar.gz`, `.deb` c/ runtime `jlink`) | `PASS` (`.zip`, `.exe`, `.msi`) | `BLOCKED` (cancelado após 15m no runner `macos-15`) | `NOT_RUN` |
| **Pacote inspecionado** | `PASS` (`rpm -qpi/qpl`, `dpkg-deb -I`) | `PASS` (`dpkg-deb -c` validou `lib/runtime/release`) | `PASS` (`unzip -l`, `Morfia.exe`, `runtime/release`) | `NOT_APPLICABLE` | `NOT_APPLICABLE` |
| **Aplicativo inicializado (empacotado)** | `PASS` (`app-image` Wayland) | `NOT_RUN` | `PASS` (`Morfia.exe` vivo por 6s sem erro no CI) | `NOT_RUN` | `NOT_RUN` |
| **Processamento validado (no pacote)** | `PASS` (11 operações multimídia reais + `ffprobe` no `app-image`) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Instalação validada** | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Desinstalação validada** | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Interação humana validada** | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
