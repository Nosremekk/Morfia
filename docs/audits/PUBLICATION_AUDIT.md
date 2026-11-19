# Auditoria Pré-Publicação (`PUBLICATION_AUDIT.md`)

**Data da auditoria:** 2026-11-19  
**Escopo:** Raiz do projeto `/home/vini/ffmpeg-visual`, arquivos versionáveis (`src/`, `docs/`, `packaging/`, `scripts/`, `.github/`, `pom.xml`, `.gitignore`, `LICENSE`) e estado do repositório Git.

---

## 1. Estado do Repositório Git
- **Raiz verificada:** `/home/vini/ffmpeg-visual/.git` (branch `main`).
- **Autorização (KD-007):** Primeiro commit local e publicação do código-fonte para `https://github.com/Nosremekk/Morfia` autorizados pelo proprietário.

## 2. Auditoria de Privacidade e Higiene
| Item | Resultado | Evidência |
| :--- | :---: | :--- |
| Credenciais, tokens, chaves SSH, `.env` | **LIMPO** | Nenhum arquivo `.env`, token ou chave privada no diretório versionável. |
| Caminhos pessoais em `src/` | **LIMPO** | `grep -rn "/home/vini" src/` -> 0 ocorrências. |
| Caminhos em `docs/` | **SANEADO / DOCUMENTADO** | Referências ao diretório de trabalho aparecem apenas em registros de auditoria locais. |
| Configurações pessoais (`~/.config/morfia`, `~/.config/ffmpeg-studio`) | **ISOLADO** | Fora do repositório; `.gitignore` bloqueia `settings.json`, `presets.json`, `queue-state.json`, `history.json`. |
| Logs e mídias de teste | **IGNORADO** | `*.log`, `logs/`, `.mp4`, `.mkv`, `.mp3`, etc. bloqueados pelo `.gitignore`. |
| Artefatos de build e distribuição | **IGNORADO** | `target/` e `dist/` excluídos pelo `.gitignore` (nenhum instalador binário no histórico Git). |
| Titular em `LICENSE` | **CONFIRMADO** | `Copyright (c) 2026 Emerson Vinicius Santos de Santana` (autorizado por KD-002). |

## 3. Auditoria de Propriedade Intelectual
- **Logotipo e ícones (`docs/branding/logo/*.svg`, `packaging/icons/*.png`, `morfia.ico`):** Criações originais do projeto sob licença MIT.
- **Ícones de UI (`IconHelper.java`):** SVG paths originais sob licença MIT.
- **Fontes:** Fontes padrão do sistema operacional, sem redistribuição de fontes proprietárias de terceiros.
- **Dependências Maven:** Listadas em `THIRD_PARTY_NOTICES.md`; FFmpeg e FFprobe não são redistribuídos.
