# Auditoria Final da Fase 6.17 (`PHASE_6_17_FINAL_AUDIT.md`)

**Data:** 2026-11-21  
**Produto:** Morfia `v1.0.0-rc.1` (`io.github.nosremekk:morfia`)  
**Repositório:** `https://github.com/Nosremekk/Morfia`

---

## 1. Consistência de Testes, FXML e Código
- **Suíte Maven (`mvn clean verify`):** **106 testes em 31 classes de teste** — 0 falhas, 0 erros, 0 ignorados.
- **Validação FXML:** **14/14 telas FXML** carregadas no Toolkit real do JavaFX 24.
- **CI Multiplatform (`44581621772`):** Aprovado (`success`) em **Linux `x86_64`**, **Windows `x86_64`** e **macOS `arm64`** após correções de portabilidade em `ConcatOperationTest`, `SubtitleOperationTest` e `PlatformAndIdentityTest`.

## 2. Empacotamento e Conformidade dos Artefatos
- **Fedora (`x86_64`):** `app-image`, `.tar.gz`, `.rpm` (`1.0.0-0.1.rc1`) e `.deb` gerados localmente e inspecionados (`rpm -qpi/qpl`, `dpkg-deb -I`). Contêm `LICENSE`, `THIRD_PARTY_NOTICES.md` e `legal/*` do OpenJDK 25; **ausência confirmada de `ffmpeg` e `ffprobe`**.
- **Validação funcional no pacote:** Executada diretamente via `app-image` em ambiente isolado com mídias sintéticas, caminhos com espaço e Unicode, rejeição de mídia corrompida, transcodificação MP4 e extração MP3 verificadas por `ffprobe`.
- **Windows (`x86_64` CI `44581631408`):** `.zip`, `.exe` e `.msi` (`0.99.1`) construídos e baixados.
- **Ubuntu 24.04 (`x86_64` CI `44581631408`):** `.deb`, `.rpm` e `.tar.gz` construídos e baixados.
- **macOS (`arm64` CI `44581631408`):** Job cancelado após 15 min no runner `macos-15`; artefato macOS não disponível nesta RC1.

## 3. Auditoria de Dependências e SBOM
- **Workflow `Dependency Audit & SBOM` (`44579257402`):** Concluído com sucesso; artefatos `bom.json`, `bom.xml` (CycloneDX) e `dependency-tree.txt` baixados.
- **Dependências runtime redistribuídas:** JavaFX 24 (`GPL-2.0 + Classpath Exception`), Jackson 2.18.2 (`Apache-2.0`), SLF4J 2.0.16 (`MIT`), Logback 1.5.16 (`EPL-1.0 / LGPL-2.1`) e OpenJDK 25.0.5 (`GPL-2.0 + Classpath Exception`).
- **Limitação:** Nenhuma varredura automática de CVEs (ex.: Trivy / OWASP) foi executada; não declaramos ausência de vulnerabilidades.

## 4. Screenshots e Testes Manuais
- **Screenshots:** Não capturados automaticamente neste ambiente Wayland sem `grim` e sem `DISPLAY` X11; nenhuma imagem fabricada foi incluída.
- **Testes interativos humanos:** Pendentes de execução pelo proprietário no Fedora, Windows e macOS conforme `docs/MANUAL_TEST_PLAN.md`.
