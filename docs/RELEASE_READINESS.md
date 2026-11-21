# Prontidão para Release (`RELEASE_READINESS.md`) — Morfia `v1.0.0-rc.1`

## Estado Atual: **PARTIALLY READY (Pronto para RC Linux/Windows com ressalvas documentadas)**

- **CI Multiplataforma (`44581621772`):** `PASS` em Linux `x86_64`, Windows `x86_64` e macOS `arm64`.
- **Artefatos construídos e com SHA-256:** Fedora Linux (`.tar.gz`, `.rpm`, `.deb`, `app-image` testado funcionalmente), Ubuntu CI (`.deb`, `.tar.gz`), Windows CI (`.zip`, `.exe`, `.msi`).
- **Pendências restantes:**
  1. Empacotamento macOS `.dmg`/`.pkg` (cancelado por tempo/fila no runner `macos-15`).
  2. Instalação/desinstalação real e testes interativos humanos em Windows, Ubuntu e Fedora.
  3. Captura manual de screenshots reais para `docs/screenshots/` e `README.md`.
  4. Varredura automatizada de CVEs sobre o SBOM CycloneDX gerado.
  5. Autorização final do proprietário para tag `v1.0.0-rc.1` e publicação da prerelease no GitHub.
