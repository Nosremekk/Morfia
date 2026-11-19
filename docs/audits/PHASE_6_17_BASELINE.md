# Baseline da Fase 6.17 (`PHASE_6_17_BASELINE.md`)

**Data:** 2026-11-19  
**Repositório:** `https://github.com/Nosremekk/Morfia`  
**Commit inicial publicado:** `b6273488f7d1cb8f64ce7e2fbbfdeacf5aa152e8` (`main` sincronizada com `origin/main`)

---

## 1. Ferramentas Disponíveis no Host Fedora (`x86_64`)

| Ferramenta | Caminho no Host | Disponibilidade |
| :--- | :--- | :---: |
| `java` / `javac` (OpenJDK 25.0.5) | `/usr/bin/java` | **Sim** |
| `mvn` (Apache Maven 3.9.11) | `/usr/bin/mvn` | **Sim** |
| `jpackage` / `jlink` / `jdeps` | `/usr/bin/jpackage`, `/usr/bin/jlink`, `/usr/bin/jdeps` | **Sim** |
| `rpmbuild` | `/usr/bin/rpmbuild` | **Sim** (instalado pelo proprietário) |
| `fakeroot` / `dpkg-deb` | `/usr/bin/fakeroot`, `/usr/bin/dpkg-deb` | **Sim** (instalado pelo proprietário) |
| `gh` (GitHub CLI) | `/usr/bin/gh` | **Sim** (autenticado como `Nosremekk`) |
| `ffmpeg` / `ffprobe` (8.1.3) | `/usr/bin/ffmpeg`, `/usr/bin/ffprobe` | **Sim** |

## 2. Suíte Baseline (`mvn clean verify`)
- **Total:** 106 testes em 31 classes, 0 falhas, 0 erros, 0 ignorados, 14/14 telas FXML validadas.
- **Divergência observada:** O run inicial `CI Multiplatform` (`44574839716`) apresentou falhas no runner Windows (`Install FFmpeg on Windows`) e `macOS arm64` (`macos-14` não suportado), que serão corrigidas na Fase 6.17.1.
