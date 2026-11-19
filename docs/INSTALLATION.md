# Instalação e Configuração do FFmpeg / FFprobe (`INSTALLATION.md`)

Este aplicativo é **local-first e 100% offline**, e **não inclui nem baixa automaticamente** executáveis do FFmpeg ou FFprobe (decisão **KD-004**). Você pode abrir o aplicativo sem FFmpeg configurado e usar o **Assistente FFmpeg** na barra lateral para configurar ou testar os binários a qualquer momento.

---

## 1. Ordem de Precedência na Descoberta de Executáveis

Implementado em `ExecutableResolver.java`:
1. **Caminho explicitamente configurado** pelo usuário nas Configurações ou no Assistente.
2. **`PATH` do sistema** (ignorando automaticamente diretórios temporários não confiáveis como `/tmp` ou `/var/tmp`).
3. **Diretórios convencionais do sistema operacional:**
   - Linux: `/usr/bin`, `/usr/local/bin`, `~/.local/bin`
   - Windows: `C:\ffmpeg\bin`, `C:\Program Files\ffmpeg\bin`
   - macOS: `/opt/homebrew/bin` (Apple Silicon), `/usr/local/bin` (Intel), `/usr/bin`
4. **Diretórios de gerenciadores de pacotes conhecidos:**
   - Linux: `/snap/bin`, `/var/lib/flatpak/exports/bin`
   - Windows: `%LOCALAPPDATA%\Microsoft\WinGet\Links`, `%USERPROFILE%\scoop\shims`, `C:\ProgramData\chocolatey\bin`
   - macOS: `/opt/local/bin` (MacPorts)

---

## 2. Instruções por Plataforma

### Linux — Fedora e Distribuições Compatíveis
- Pacote padrão dos repositórios Fedora:
  ```bash
  sudo dnf install ffmpeg-free
  ```
- Caso necessite de encoders adicionais (como `libx264` / `libx265` completos não presentes em `ffmpeg-free`), o usuário pode optar por configurar o repositório RPM Fusion conforme sua própria escolha e política local.

### Linux — Debian e Ubuntu
```bash
sudo apt update && sudo apt install ffmpeg
```

### Windows (x86_64)
- **Via Winget (quando disponível):**
  ```powershell
  winget install Gyan.FFmpeg
  ```
- **Instalação Manual:** Baixe um build confiável (ex.: Gyan.dev ou BtbN), extraia para uma pasta fixa como `C:\ffmpeg` (contendo `C:\ffmpeg\bin\ffmpeg.exe` e `ffprobe.exe`) e aponte os executáveis manualmente na **Etapa 6 do Assistente FFmpeg** (ou adicione ao `PATH` do usuário).

### macOS (Apple Silicon `arm64` e Intel `x86_64`)
- **Via Homebrew:**
  ```bash
  brew install ffmpeg
  ```
  - Em Macs **Apple Silicon (`arm64`)**, o Homebrew instala em `/opt/homebrew/bin/ffmpeg`.
  - Em Macs **Intel (`x86_64`)**, instala em `/usr/local/bin/ffmpeg`.
  - Aplicações iniciadas pelo Finder recebem um `PATH` reduzido; por isso o `ExecutableResolver` verifica explicitamente `/opt/homebrew/bin` e `/usr/local/bin`.
- **Compatibilidade de arquitetura:** Certifique-se de usar a mesma arquitetura entre o runtime Java do pacote e os executáveis FFmpeg instalados (ou Rosetta 2 quando aplicável).
