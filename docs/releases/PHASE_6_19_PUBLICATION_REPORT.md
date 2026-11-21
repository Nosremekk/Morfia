# Relatório de Publicação Oficial da Primeira Release Candidate (`v1.0.0-rc.1`)
## Fase 6.19 — Morfia (Multimedia Processing Studio)

- **Data da Publicação:** 21 de Novembro de 2026
- **Status:** **PUBLISHED**
- **Repositório Oficial:** [https://github.com/Nosremekk/Morfia](https://github.com/Nosremekk/Morfia)
- **URL da Release:** [https://github.com/Nosremekk/Morfia/releases/tag/v1.0.0-rc.1](https://github.com/Nosremekk/Morfia/releases/tag/v1.0.0-rc.1)
- **Proprietário / Titular:** Emerson Vinicius Santos de Santana
- **Licença:** MIT (2026)
- **Tag Oficial:** `v1.0.0-rc.1`
- **Commit Alvo dos Binários:** `ba370c1831bc97f7af2e76561095d591a3709fac`
- **Classificação:** Prerelease pública (`isPrerelease: true`)

---

## 1. Resumo Executivo

Em conformidade com a autorização expressa do titular Emerson Vinicius Santos de Santana, a primeira Release Candidate do **Morfia** (`v1.0.0-rc.1`) foi formalmente publicada no GitHub. A tag anotada `v1.0.0-rc.1` foi criada apontando estritamente para o commit de consolidação dos binários (`ba370c1831bc97f7af2e76561095d591a3709fac`), e todos os 6 binários auditados para Linux e Windows, acompanhados do manifesto de integridade `SHA256SUMS`, foram enviados com sucesso.

---

## 2. Artefatos Oficiais Publicados

A release contém exatamente **7 ativos** (6 pacotes de instalação/distribuição e 1 manifesto de checksums), sem qualquer inclusão de artefatos não autorizados (como binários parciais de macOS):

| Ativo | Plataforma / Arquitetura | Formato | Tamanho (Bytes) | SHA-256 Digest |
| :--- | :--- | :---: | :---: | :--- |
| `morfia-1.0.0-rc.1-linux-x64.tar.gz` | Linux x86_64 (Universal) | `tar.gz` | `103.288.410` | `4cf43eb144302d6616c905b005f0e21a42168ba72b68ba79dd7e2957d5b0b10f` |
| `morfia-1.0.0-0.1.rc1.x86_64.rpm` | Linux x86_64 (Fedora / RHEL) | `rpm` | `78.690.456` | `bacb3a35bf68d9e1d1816106da12d5473f17679b0491fa36a96b645129ad23be` |
| `morfia_1.0.0-0.1.rc1_amd64.deb` | Linux x86_64 (Ubuntu 24.04 / Debian) | `deb` | `47.820.130` | `c90e9b1e6f1db125f86176ec98daa57b29dd49952ef5bc220a979df586f1aba4` |
| `morfia-1.0.0-rc.1-windows-x64.zip` | Windows x86_64 (Portátil) | `zip` | `60.694.929` | `261c383fdf48ae354864a76f647f9e7c4e489e6c8093fc674cd158c0a224a57e` |
| `Morfia-0.99.1.exe` | Windows x86_64 (Instalador) | `exe` | `62.173.696` | `2b4205c844b3f9c821c22b129db65b71509b863e1f0f4eb443e3598803fb19ed` |
| `Morfia-0.99.1.msi` | Windows x86_64 (Instalador MSI) | `msi` | `61.588.248` | `92ef82f8d137f943bd204e3937e3806330eba075002f01bf0e6690c6cf00929f` |
| `SHA256SUMS` | Manifesto de Integridade | `txt` | `564` | `146e1b42901061f7c0bae5ca967467f47ee02b5a6d71cef2dfe328e048c1aff8` |

---

## 3. Verificações Pré-Publicação Realizadas

1. **Estado do Repositório Git:**
   - Branch `main` limpa e sincronizada com `origin/main` (`c3a8edc`).
   - Nenhuma tag preexistente colidindo com `v1.0.0-rc.1`.
2. **Autenticação e Permissões:**
   - `gh auth status` confirmou sessão ativa da conta proprietária `Nosremekk`.
3. **Verificação de Testes (`mvn clean verify`):**
   - **106 testes executados e aprovados com 100% de sucesso**.
   - 31 classes de teste unitárias e de integração.
   - 14 telas FXML carregadas com êxito no toolkit JavaFX real (`FxmlLoadingTest`).
   - Zero falhas, zero erros e zero testes ignorados.
4. **Verificação de Segurança (Trivy CVE):**
   - 0 vulnerabilidades identificadas no SBOM CycloneDX após a atualização de dependências para Jackson `2.18.11` e Logback `1.5.34`.
5. **Verificação Byte-a-Byte dos Binários:**
   - Todos os 6 binários em disco foram verificados contra seus hashes SHA-256 e tamanhos em bytes, obtendo 100% de conformidade com os artefatos de CI e empacotamento local.

---

## 4. Evidências de Publicação e Verificação Remota

### 4.1 Criação e Push da Tag
```bash
git tag -a v1.0.0-rc.1 ba370c1831bc97f7af2e76561095d591a3709fac -m "Morfia v1.0.0-rc.1 (Release Candidate)"
git push origin v1.0.0-rc.1
```
- Tag remota verificada via `git ls-remote --tags origin`:
  - Tag object: `6536f1b97816bfcc9911ec5ef0844b538f132486` -> `refs/tags/v1.0.0-rc.1`
  - Tagged commit: `ba370c1831bc97f7af2e76561095d591a3709fac` -> `refs/tags/v1.0.0-rc.1^{}`

### 4.2 Verificação via GitHub CLI (`gh release view`)
```json
{
  "assets": [
    { "name": "Morfia-0.99.1.exe", "size": 62173696, "state": "uploaded" },
    { "name": "Morfia-0.99.1.msi", "size": 61588248, "state": "uploaded" },
    { "name": "morfia-1.0.0-0.1.rc1.x86_64.rpm", "size": 78690456, "state": "uploaded" },
    { "name": "morfia-1.0.0-rc.1-linux-x64.tar.gz", "size": 103288410, "state": "uploaded" },
    { "name": "morfia-1.0.0-rc.1-windows-x64.zip", "size": 60694929, "state": "uploaded" },
    { "name": "morfia_1.0.0-0.1.rc1_amd64.deb", "size": 47820130, "state": "uploaded" },
    { "name": "SHA256SUMS", "size": 564, "state": "uploaded" }
  ],
  "isPrerelease": true,
  "tagName": "v1.0.0-rc.1",
  "url": "https://github.com/Nosremekk/Morfia/releases/tag/v1.0.0-rc.1"
}
```

---

## 5. Governança, Limitações e Orientações

1. **Classificação como Prerelease:** A release está estritamente categorizada como `isPrerelease: true`, preservando a distinção entre a versão candidata e a futura versão `v1.0.0` estável.
2. **Sem Binários de macOS nesta RC1:** Em virtude do timeout nos runners de empacotamento da plataforma Apple, o instalador macOS foi postergado para a release `v1.0.0`.
3. **Dependência Externa de FFmpeg/FFprobe:** O Morfia opera de forma 100% offline e local-first, utilizando as ferramentas multimídia instaladas no sistema hospedeiro. O assistente de configuração em 9 etapas guia o usuário na detecção e teste dos binários.
4. **Assinatura Digital no Windows:** Os instaladores Windows não possuem assinatura Authenticode comercial; avisos do Windows SmartScreen são esperados durante o período de pré-lançamento.
5. **Validação Comunitária:** Relatos de testes, sugestões ou problemas devem ser encaminhados via GitHub Issues:
   [https://github.com/Nosremekk/Morfia/issues](https://github.com/Nosremekk/Morfia/issues)
