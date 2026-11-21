# Auditoria Automatizada de Vulnerabilidades (`DEPENDENCY_SECURITY_AUDIT.md`)

**Data:** 2026-11-21  
**Ferramenta:** Trivy (`trivy sbom target/bom.json`) via GitHub Actions (`dependency-audit.yml`)  
**Escopo:** Dependências Maven declaradas e transitivas no SBOM CycloneDX (`target/bom.json`).

---

## 1. Primeira Varredura (Baseline `1.0.0-rc.1` inicial — Run `44929232698` / Análise local do SBOM `44579257402`)
- **Total encontrado:** **21 vulnerabilidades** (0 `CRITICAL`, 8 `HIGH`, 10 `MEDIUM`, 3 `LOW`).
- **Componentes afetados:**
  - `com.fasterxml.jackson.core:jackson-core` `2.18.2`: 3 `HIGH` (`CVE-2026-68494`, `CVE-2026-89407`, `CVE-2026-89425`) + 1 `MEDIUM` (`CVE-2026-18401`).
  - `com.fasterxml.jackson.core:jackson-databind` `2.18.2`: 5 `HIGH` (`CVE-2026-54512`, `CVE-2026-54513`, `CVE-2026-68497`, `CVE-2026-91776`, `CVE-2026-91777`) + 8 `MEDIUM` (`CVE-2026-19032`, `CVE-2026-54514`, `CVE-2026-54515`, `CVE-2026-59888`, `CVE-2026-59889`, `CVE-2026-77310`, `CVE-2026-83557`, `GHSA-mhm7-754m-9p8w`).
  - `ch.qos.logback:logback-core` `1.5.16`: 1 `MEDIUM` (`CVE-2025-11226`) + 3 `LOW` (`CVE-2026-10532`, `CVE-2026-1225`, `CVE-2026-9828`).
- **Correção aplicada (Commit `ba370c1`):**
  - Atualização pontual de patch versions em `pom.xml`:
    - `jackson.version`: `2.18.2` -> **`2.18.11`**
    - `logback.version`: `1.5.16` -> **`1.5.34`**

---

## 2. Varredura Pós-Correção (Commit `ba370c1` — Run `44933075538`)
- **URL da execução:** https://github.com/Nosremekk/Morfia/actions/runs/44933075538
- **Resultado em `target/trivy-sbom-report.json`:** **`TOTAL_VULNS=0`** (0 `CRITICAL`, 0 `HIGH`, 0 `MEDIUM`, 0 `LOW`).

---

## 3. Limitações da Análise
- A varredura via `trivy sbom target/bom.json` cobre as dependências Java/Maven listadas no SBOM CycloneDX.
- Não substitui auditoria de vulnerabilidades do runtime OpenJDK do sistema operacional hospedeiro ou dos binários externos FFmpeg/FFprobe instalados pelo usuário.
