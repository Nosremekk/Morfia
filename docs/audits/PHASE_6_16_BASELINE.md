# Baseline da Fase 6.16 (`PHASE_6_16_BASELINE.md`)

**Data da auditoria:** 2026-11-19  
**Diretório do projeto:** `/home/vini/ffmpeg-visual`

---

## 1. Ambiente e Versões Detectadas

| Componente | Valor Real Observado |
| :--- | :--- |
| **Sistema Operacional** | Fedora Linux (`7.3.3-200.fc44.x86_64`), arquitetura `x86_64` (`amd64`) |
| **Ambiente Gráfico** | Wayland (`WAYLAND_DISPLAY=wayland-0`, `DISPLAY=` vazio) |
| **Espaço em Disco (`/home`)** | 275 GB total, 199 GB usados, **75 GB disponíveis** (73% uso) |
| **Java / JDK** | OpenJDK `25.0.5` (`Red_Hat-25.0.5.0.8-3`, build `25.0.5+8`) |
| **Apache Maven** | `3.9.11` (`Red Hat 3.9.11-11`) |
| **FFmpeg / FFprobe (Host)** | `8.1.3` (`built with gcc 16.2.0`) |
| **jpackage / jlink / jdeps (Host)** | Presentes no host em `/usr/bin/jpackage`, `/usr/bin/jlink`, `/usr/bin/jdeps` (invocáveis via `flatpak-spawn --host` a partir do sandbox Flatpak do IDE) |
| **dpkg-deb (Host)** | Presente em `/usr/bin/dpkg-deb` |
| **rpmbuild (Host)** | **Ausente** (`which: no rpmbuild in (/usr/local/bin:/usr/bin)`) — requer pacote `rpm-build` (`sudo dnf install rpm-build`, não executado automaticamente) |
| **rsvg-convert** | Presente em `/usr/bin/rsvg-convert` (renderização SVG -> PNG) |
| **GitHub CLI (`gh`)** | **Ausente** no container e no host (`which: no gh`) |
| **Autenticação Git / SSH** | `ssh -T git@github.com` autenticado como conta **`Nosremekk`** (`Hi Nosremekk! You've successfully authenticated`); `git config user.name`: `nosremekk`; `git remote -v`: vazio; `git ls-remote git@github.com:Nosremekk/Morfia.git`: `Repository not found` |

---

## 2. Estado Inicial do Projeto (Pré-6.16)

- **Artefato Maven inicial (`pom.xml`):** `com.ffmpegstudio:ffmpeg-studio:0.1.0-SNAPSHOT` -> divergente da versão planejada `1.0.0-rc.1`.
- **Repositório Git:** Inicializado na branch `main`, sem commits (`fatal: your current branch 'main' does not have any commits yet`).
- **Suíte pré-6.16:** 106 testes em 31 classes, 14 telas FXML validadas no JavaFX 24 Toolkit.

---

## 3. Bloqueadores Reais Identificados no Baseline

1. **`rpmbuild` ausente no host:** Impede geração de `.rpm` sem instalação prévia de `rpm-build` via `sudo dnf install rpm-build` (não executado por regra de segurança). `app-image`, `.tar.gz` e `.deb` (via `dpkg-deb`) permanecem viáveis.
2. **Repositório `Nosremekk/Morfia` inexistente + `gh` ausente:** Embora a autenticação SSH para `Nosremekk` funcione, não é possível criar o repositório remoto via CLI sem `gh` ou token API; se o repositório não existir no GitHub no momento do `push`, o `push` será bloqueado até criação manual na interface web pelo proprietário.
