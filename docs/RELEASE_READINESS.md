# Prontidão para Release (`RELEASE_READINESS.md`) — Morfia `1.0.0-rc.1`

## 1. Status Geral
- **Produto:** Morfia (`io.github.nosremekk:morfia`)
- **Versão alvo:** `1.0.0-rc.1` (Fase 6.16 — Identidade Definitiva, Publicação e Release Engineering)
- **Ambiente validado localmente:** Fedora Linux 44 (`x86_64`), OpenJDK `25.0.5`, JavaFX `24`, Maven `3.9.11`, FFmpeg `8.1.3`.

## 2. Checklist de Consolidação
- [x] **KD-001 (Morfia):** `AppIdentity.java` consolidado; UI, `pom.xml`, scripts, workflows, i18n e migração não destrutiva `~/.config/ffmpeg-studio` -> `~/.config/morfia` implementados.
- [x] **KD-002 (Licença MIT):** `LICENSE` com `Copyright (c) 2026 Emerson Vinicius Santos de Santana`, `THIRD_PARTY_NOTICES.md`, `docs/LICENSING.md` e `pom.xml`.
- [x] **KD-003 (Multiplataforma):** Camada `infrastructure/platform`, scripts `packaging/` e matriz `docs/MULTIPLATFORM_VALIDATION.md`.
- [x] **KD-004 (Assistente FFmpeg):** `SetupWizardController` (9 etapas) e zero binários FFmpeg/FFprobe embutidos.
- [x] **KD-006 (Identidade Visual Retrô):** Paleta `#171B18` / `#292E29` / `#454D40` / `#B7C99B` / `#D5C7A5` / `#B88E60` em `dark-theme.css`, logotipo SVG original e PNGs/ICO em `packaging/icons/`.
- [x] **KD-008 / 6.16.6 (Empacotamento Linux):** `app-image` e `morfia-1.0.0-rc.1-linux-x64.tar.gz` gerados via `jpackage` com OpenJDK 25 integrado e validados por smoke test real fora do repositório.

## 3. Pendências / Bloqueadores para `1.0.0` Final
1. **Publicação GitHub (`Nosremekk/Morfia`):** Criação do repositório vazio no GitHub pelo proprietário (ou instalação de `gh` autenticado) caso ainda não exista no momento do `push`.
2. **Pacotes RPM/DEB locais e CI Multiplataforma:** `rpmbuild` e `fakeroot` ausentes no host local; execução real dos workflows GitHub Actions em Linux, Windows (`x86_64`) e macOS (`arm64` / `x86_64`) pendente.
3. **Aprovação final:** Decisão do proprietário após revisão da `v1.0.0-rc.1` antes de promover para `1.0.0`.
