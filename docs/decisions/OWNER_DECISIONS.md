# Registro de Decisões do Proprietário (Owner Decisions)

Este documento consolida todas as decisões estratégicas aprovadas pelo proprietário do projeto **Morfia** (anteriormente denominado provisoriamente *FFmpeg Studio*) para a versão **`1.0.0-rc.1`** (Fase 6.16 — Identidade Definitiva, Publicação e Release Engineering).

---

## Resumo Executivo

| ID | Título | Decisão Aprovada | Estado |
| :--- | :--- | :--- | :--- |
| **KD-001** | Nome definitivo | **Morfia** (`morfia`) — *FFmpeg Studio* deixa de ser a identidade pública | **APROVADA** |
| **KD-002** | Licenciamento | **MIT (`SPDX: MIT`)**, `Copyright (c) 2026 Emerson Vinicius Santos de Santana` | **APROVADA** |
| **KD-003** | Plataformas | **Linux**, **Windows** e **macOS** (planejadas; validação por runner/SO) | **APROVADA** |
| **KD-004** | Motor multimídia | **FFmpeg e FFprobe externos** via assistente de primeiro uso (sem bundle) | **APROVADA** |
| **KD-005** | Publicação | Repositório público oficial: `https://github.com/Nosremekk/Morfia` | **APROVADA** |
| **KD-006** | Identidade visual | **Retrô**, inspirada em estações multimídia dos anos 1990 e 2000 | **APROVADA** |
| **KD-007** | Autorização Git | Auditoria, `git add`, 1º commit, criação/configuração de remote e `push` de `main` (sem force push) | **APROVADA** |
| **KD-008** | Empacotamento | Construir todos os formatos viáveis no Fedora + preparar/executar workflows multiplataforma | **APROVADA** |
| **KD-009** | Versão | **`1.0.0-rc.1`** (não promover automaticamente para `1.0.0` final) | **APROVADA** |

---

## Detalhamento e Consequências

### KD-001 — Nome definitivo: Morfia
- **Descrição:** O nome público do produto passa a ser **Morfia** (nome técnico `morfia`, subtítulo `Multimedia Processing Studio`), encerrando o uso do nome provisório *FFmpeg Studio* na interface e documentação pública.
- **Consequências técnicas:** Centralização em `AppIdentity.java`; atualização de título, Dashboard, barra lateral, tela Sobre, Assistente, `pom.xml`, scripts, workflows e i18n (`pt_BR`/`en`). O namespace Java `com.ffmpegstudio.*` é preservado na `1.0.0-rc.1` para evitar refatoração estética de alto risco; o diretório legado `~/.config/ffmpeg-studio` é migrado de forma não destrutiva e idempotente para `~/.config/morfia`.
- **Pendências:** Nenhuma técnica; registro formal de marca (se desejado pelo proprietário) permanece fora do escopo automático.

### KD-002 — Licenciamento: MIT
- **Descrição:** Licença **MIT** (`SPDX: MIT`) com titular confirmado: **Emerson Vinicius Santos de Santana** (ano inicial `2026`).
- **Consequências:** Remoção de todos os marcadores `[PENDENTE]` em `LICENSE`, `AppIdentity.java`, `pom.xml`, `THIRD_PARTY_NOTICES.md`, `LICENSING.md` e tela Sobre. Manutenção da separação entre código autoral MIT e executáveis externos FFmpeg/FFprobe.

### KD-003 — Plataformas: Linux, Windows e macOS
- **Descrição:** Suporte planejado para Linux (`x86_64`), Windows (`x86_64`) e macOS (`arm64` e `x86_64`), diferenciando claramente ambientes validados (`Fedora Linux x86_64`) de ambientes apenas preparados/não validados localmente (`Windows`, `macOS`, `Ubuntu/Debian`).

### KD-004 — Motor multimídia: FFmpeg e FFprobe externos
- **Descrição:** Nenhum instalador inclui FFmpeg ou FFprobe. O Assistente de Configuração de 9 etapas guia o usuário na detecção, verificação de versões/capacidades, seleção manual e instruções por plataforma.

### KD-005 — Publicação: `https://github.com/Nosremekk/Morfia`
- **Descrição:** Repositório público oficial definido em `https://github.com/Nosremekk/Morfia`.
- **Consequências:** Atualização de `AppIdentity.OFFICIAL_URL`, `pom.xml` (`<url>`, `<scm>`), `.github/ISSUE_TEMPLATE/config.yml`, `README.md` e workflows.

### KD-006 — Identidade visual: Retrô (anos 1990/2000)
- **Descrição:** Estética autoral inspirada em estações de trabalho multimídia clássicas (geometria, controles compactos, hierarquia funcional, tipografia técnica monoespaçada para métricas, paleta escura `#171B18` / `#292E29` / `#454D40` / `#B7C99B` / `#D5C7A5` / `#B88E60` mantendo tema claro acessível e zero scanlines/efeitos que prejudiquem leitura). Logotipo original em SVG (`M` geométrico modular) + PNGs multi-resolução + ICO.

### KD-007 — Autorização Git
- **Descrição:** Autorizados auditoria, `git add`, primeiro commit local, configuração do `origin` para `Nosremekk/Morfia` e `git push` da branch `main` sem `--force`.
- **Restrição:** Não autoriza criação de tag definitiva `v1.0.0` nem publicação automática de release estável.

### KD-008 — Empacotamento
- **Descrição:** Geração local dos formatos viáveis no Fedora (`app-image`, `.tar.gz`, `.deb` via `dpkg-deb` quando viável; documentação de `rpm-build` caso ausente) e preparação dos workflows GitHub Actions multiplataforma.

### KD-009 — Versão: `1.0.0-rc.1`
- **Descrição:** Correção da divergência anterior (`0.1.0-SNAPSHOT` -> `1.0.0-rc.1`) no `pom.xml`, `AppIdentity`, artefatos (`morfia-1.0.0-rc.1.jar`), documentação e scripts, com mapeamento numérico documentado (`1.0.0`) apenas onde exigido por `jpackage`/RPM/DEB/MSI/DMG.
