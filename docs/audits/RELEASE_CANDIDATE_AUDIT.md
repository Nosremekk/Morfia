# Auditoria da Release Candidate (`RELEASE_CANDIDATE_AUDIT.md`) — `v1.0.0-rc.1`

**Data:** 2026-11-19  
**Versão auditada:** `1.0.0-rc.1`

---

## 1. Consistência de Identidade, Licença e Versão
- `pom.xml`: `io.github.nosremekk:morfia:1.0.0-rc.1` -> gera `target/morfia-1.0.0-rc.1.jar`.
- `AppIdentity.java`: `DISPLAY_NAME="Morfia"`, `VERSION="1.0.0-rc.1"`, `NATIVE_VERSION="1.0.0"`, `LICENSE_SPDX="MIT"`, `COPYRIGHT_HOLDER="Emerson Vinicius Santos de Santana"`.
- `LICENSE`: MIT oficial com titular confirmado e zero marcadores `[PENDENTE]`.
- Validação automática: `PlatformAndIdentityTest.shouldExposeCentralizedIdentity` confirma alinhamento direto entre `AppIdentity` e `pom.xml`.

## 2. Segurança e Licenciamento da Distribuição
- **Auditoria de dependências:** Inventário completo em `THIRD_PARTY_NOTICES.md` (OpenJDK 25 + JavaFX 24 `GPL-2.0 + Classpath Exception`, Jackson 2.18.2 `Apache-2.0`, SLF4J 2.0.16 `MIT`, Logback 1.5.16 `EPL-1.0/LGPL-2.1`).
- **SBOM (CycloneDX):** Workflow `.github/workflows/dependency-audit.yml` configurado para gerar `target/bom.json` e `target/bom.xml` via `cyclonedx-maven-plugin`; não executado localmente nem no GitHub Actions ainda.
- **Vulnerabilidades (CVE):** Nenhuma auditoria automatizada de CVEs (ex.: OWASP Dependency-Check / Trivy) foi executada neste ambiente; portanto, **não declaramos ausência de vulnerabilidades** nas dependências de terceiros.
- **Assinatura de código:** Windows (sem Authenticode -> aviso SmartScreen) e macOS (sem Developer ID / notarização -> bloqueio Gatekeeper para downloads públicos) documentados em `docs/PACKAGING.md`.

## 3. Artefatos e Validação
- `dist/1.0.0-rc.1/linux-x64/app-image/morfia/`: Gerado via `jpackage` com OpenJDK 25 integrado e validado por smoke test real fora do repositório.
- `dist/1.0.0-rc.1/linux-x64/morfia-1.0.0-rc.1-linux-x64.tar.gz`: Gerado (99 MB) com SHA-256 verificado.
- `.rpm` e `.deb` locais: Não gerados neste host por ausência de `rpmbuild` e `fakeroot` (documentado).
