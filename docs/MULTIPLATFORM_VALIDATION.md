# Matriz de Validação Multiplataforma — Morfia `1.0.0-rc.1`

Este documento registra a evidência real por plataforma. **Nenhum critério não executado foi marcado como `PASS`.**

**Estados:** `PASS` · `FAIL` · `PARTIAL` · `BLOCKED` · `NOT_RUN` · `NOT_APPLICABLE`

---

## Matriz por Plataforma e Critério

| Critério | Linux Fedora (`x86_64`) | Linux Ubuntu 24.04 (`x86_64` CI) | Windows (`x86_64` CI) | macOS (`arm64` CI) | macOS (`x86_64`) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Código compilado** | `PASS` | `PASS` (Run `44581621772`) | `PASS` (Run `44581621772`) | `PASS` (Run `44581621772`) | `NOT_RUN` |
| **Testes executados (106 testes / 14 FXML)** | `PASS` | `PASS` (Xvfb) | `PASS` (106/106) | `PASS` (106/106) | `NOT_RUN` |
| **Pacote construído** | `PASS` (`app-image`, `.tar.gz`, `.rpm`, `.deb`) | `PASS` (`.tar.gz`, `.deb`, `.rpm` no Run `44581631408`) | `PASS` (`.zip`, `.exe`, `.msi` no Run `44581631408`) | `BLOCKED` (`cancelled` após 15m no runner `macos-15`) | `NOT_RUN` |
| **Pacote inspecionado** | `PASS` (`rpm -qpi/qpl`, `dpkg-deb -I`) | `PASS` (`dpkg-deb -I` local) | `PARTIAL` (artefatos baixados e verificados SHA-256; sem inspeção MSI nativa) | `NOT_RUN` | `NOT_APPLICABLE` |
| **Aplicativo inicializado (empacotado)** | `PASS` (smoke test Wayland fora do repo) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Processamento validado (no pacote)** | `PASS` (análise, rejeição corrompido, MP4, MP3, Unicode/espaços via `app-image`) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Instalação validada** | `NOT_RUN` (não instalado via `sudo dnf install`) | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Desinstalação validada** | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |
| **Interação humana validada** | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` | `NOT_RUN` |

---

## Observações Técnicas Relevantes

1. **Aceleração por Hardware (`VAAPI`):** No host Fedora, a sondagem em 3 níveis detecta corretamente `[h264_vaapi] No usable encoding profile found` e mantém execução explícita em software sem falso positivo.
2. **Assinatura de Código:** Ausência de certificado Authenticode (Windows) e Developer ID / notarização Apple (macOS) não bloqueia a release candidate comunitária, desde que explicitamente documentada.
3. **Runner Ubuntu vs Fedora (`jpackage`):** O `.deb` gerado no runner Ubuntu (`11.3 MB`) usou `jlink` automático de módulos reduzidos, enquanto o do Fedora (`73 MB`) usou `--runtime-image` completo; recomenda-se validar a execução do `.deb` do Ubuntu em container/VM Ubuntu antes de promovê-lo como estável.
