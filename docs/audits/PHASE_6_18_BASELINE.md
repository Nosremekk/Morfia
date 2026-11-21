# Baseline da Fase 6.18 (`PHASE_6_18_BASELINE.md`)

**Data:** 2026-11-21  
**Repositório:** `https://github.com/Nosremekk/Morfia`  
**Branch:** `main` (sincronizada com `origin/main` no commit `b31ddec`)  
**Commit dos artefatos anteriores:** `30628a6e2b31af089125c5625ec7960daa6a4cd5`

---

## 1. Verificação Inicial do Repositório e CI
- `git status`: limpo (`## main...origin/main`).
- `CI Multiplatform`: último run (`44581621772` e `449283...`) aprovado (`success`) em Linux `x86_64`, Windows `x86_64` e macOS `arm64`.
- `Dependency Audit & SBOM` (`44579257402`): `success` (SBOM CycloneDX gerado, porém sem scanner de CVEs executado ainda).
- `Packaging (jpackage Multiplatform)` (`44581631408`): `Package (linux-x64)` `success`, `Package (windows-x64)` `success`, `Package (macos-arm64)` `cancelled` após ~15m (blob de log inexistente — cancelamento em fila/execução no runner `macos-15`).

## 2. Achado Crítico na Auditoria dos Artefatos Linux (Ubuntu vs. Fedora)
- **DEB Fedora (`73.074.504` bytes):** Contém `/opt/morfia/lib/runtime/` completo (OpenJDK 25.0.5) + `/opt/morfia/lib/app/*`.
- **DEB Ubuntu (`11.336.466` bytes):** Inspeção via `dpkg-deb -c` revelou **ausência do diretório `/opt/morfia/lib/runtime/`** — apenas `/opt/morfia/lib/app/*` foi empacotado, o que tornaria o `.deb` do Ubuntu dependente de Java pré-instalado.
- **Ação corretiva planejada:** Atualizar `packaging/linux/package-linux.sh` para gerar explicitamente um runtime `jlink` completo quando `jmods/` estiver presente (como no Temurin 25 do Ubuntu) e validar no CI a presença obrigatória de `lib/runtime/release` dentro do `.deb`.
- **Windows ZIP (`60.683.400` bytes):** Inspeção via `unzip -l` confirmou presença de `runtime/release`, `Morfia.exe`, `app/javafx-*-win.jar`, `app/LICENSE` e `app/THIRD_PARTY_NOTICES.md`.
