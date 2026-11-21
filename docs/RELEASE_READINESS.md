# Prontidão para Release (`RELEASE_READINESS.md`) — Morfia `v1.0.0-rc.1`

## Estado Atual: **READY FOR RC PUBLICATION (Linux x64 e Windows x64; macOS pendente fora da RC1)**

- **Testes automatizados (`mvn clean verify`):** 106/106 aprovados (31 classes, 14/14 FXML).
- **CI Multiplatform (`44933069499`):** `PASS` em Linux `x86_64`, Windows `x86_64` e macOS `arm64`.
- **Packaging (`44933078817`) + Fedora local:** `.tar.gz`, `.rpm`, `.deb` (com runtime `jlink` verificado no Ubuntu!), Windows `.zip` (com smoke test `Morfia.exe` aprovado), `.exe` e `.msi`.
- **Auditoria de Vulnerabilidades Trivy (`44933075538`):** **`0` vulnerabilidades** no SBOM CycloneDX após upgrade para Jackson `2.18.11` e Logback `1.5.34`.
- **Validação funcional no pacote (`app-image` Linux):** 11 operações multimídia reais + `ffprobe` aprovados.
- **Pendências manuais (não bloqueantes para prerelease comunitária):** Testes interativos humanos em Windows/Ubuntu/Fedora e captura manual de screenshots (Wayland bloqueia captura automática sem `grim`).
