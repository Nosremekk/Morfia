# Prontidão para Release (`RELEASE_READINESS.md`) — Morfia `v1.0.0-rc.1`

## Estado Atual: **PUBLISHED** (Primeira Release Candidate `v1.0.0-rc.1` publicada oficialmente)

- **URL Oficial da Release:** https://github.com/Nosremekk/Morfia/releases/tag/v1.0.0-rc.1
- **Tag:** `v1.0.0-rc.1` (aponta para `ba370c1831bc97f7af2e76561095d591a3709fac`)
- **Tipo de Release:** Pre-release pública
- **Data de Publicação:** 21/11/2026
- **Licença:** MIT — Copyright (c) 2026 Emerson Vinicius Santos de Santana
- **Testes automatizados (`mvn clean verify`):** 106/106 aprovados (31 classes de teste, 14/14 telas FXML validadas no toolkit real JavaFX).
- **CI Multiplatform (`44933069499`):** `PASS` em Linux `x86_64`, Windows `x86_64` e macOS `arm64`.
- **Auditoria de Vulnerabilidades Trivy (`44933075538`):** **`0` vulnerabilidades** no SBOM CycloneDX.
- **Validação Funcional no Pacote:** 11 operações multimídia reais + inspeção FFprobe validadas com sucesso no pacote Linux.

---

## Tabela de Artefatos Oficiais Publicados (6 Binários + SHA256SUMS)

| Artefato | Plataforma | Formato | Tamanho (bytes) | Hash SHA-256 |
| :--- | :--- | :---: | :---: | :--- |
| `morfia-1.0.0-rc.1-linux-x64.tar.gz` | Linux (Fedora/Universal) | `tar.gz` | `103288410` | `4cf43eb144302d6616c905b005f0e21a42168ba72b68ba79dd7e2957d5b0b10f` |
| `morfia-1.0.0-0.1.rc1.x86_64.rpm` | Linux (Fedora / RHEL) | `rpm` | `78690456` | `bacb3a35bf68d9e1d1816106da12d5473f17679b0491fa36a96b645129ad23be` |
| `morfia_1.0.0-0.1.rc1_amd64.deb` | Linux (Ubuntu 24.04 / Debian) | `deb` | `47820130` | `c90e9b1e6f1db125f86176ec98daa57b29dd49952ef5bc220a979df586f1aba4` |
| `morfia-1.0.0-rc.1-windows-x64.zip` | Windows `x64` (Portátil) | `zip` | `60694929` | `261c383fdf48ae354864a76f647f9e7c4e489e6c8093fc674cd158c0a224a57e` |
| `Morfia-0.99.1.exe` | Windows `x64` (Instalador) | `exe` | `62173696` | `2b4205c844b3f9c821c22b129db65b71509b863e1f0f4eb443e3598803fb19ed` |
| `Morfia-0.99.1.msi` | Windows `x64` (Instalador MSI) | `msi` | `61588248` | `92ef82f8d137f943bd204e3937e3806330eba075002f01bf0e6690c6cf00929f` |
| `SHA256SUMS` | Todas | `checksum` | `564` | `146e1b42901061f7c0bae5ca967467f47ee02b5a6d71cef2dfe328e048c1aff8` |

---

## Escopo e Observações da Release Candidate
1. **macOS:** Não incluído nos binários da RC1 (o build nativo `.dmg`/`.pkg` com JavaFX requer tempo superior aos runners padrão de CI; planejado para a release estável `v1.0.0`).
2. **FFmpeg e FFprobe Externos:** O Morfia não embute os binários do FFmpeg/FFprobe por design (KD-004). O usuário deve instalá-los e validá-los pelo Assistente FFmpeg integrado.
3. **Assinatura Digital no Windows:** Instaladores sem certificado Authenticode comercial; aviso do Windows SmartScreen esperado em primeira execução.
