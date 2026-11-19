# Estratégia e Processo de Release (`RELEASING.md`)

Este projeto adota **Semantic Versioning (SemVer)** e um ciclo controlado de Release Candidates antes da versão final `1.0.0`.

---

## 1. Sequência de Lançamento Planejada

1. **`1.0.0-rc.1`** (Atual — Fase 6 concluída localmente em preparação)
   - Consolidação das decisões KD-001 a KD-005, assistente FFmpeg, camada multiplataforma, licenciamento MIT e workflows.
2. **Validação Multiplataforma e Correções**
   - Execução dos workflows em runners nativos (Linux `x86_64`, Windows `x86_64`, macOS `arm64`/`x86_64`).
   - Registro de evidências em `docs/MULTIPLATFORM_VALIDATION.md`.
3. **Escolha Definitiva de Identidade & Titular**
   - Confirmação do nome definitivo (KD-001), logotipo e titular em `LICENSE` (KD-002).
4. **Aprovação Final -> `1.0.0`**
   - Criação de tag anotada e publicação oficial apenas após autorização expressa do proprietário.

---

## 2. Convenção de Nomenclatura de Artefatos

Padrão: `<produto>-<versão>-<sistema>-<arquitetura>.<extensão>`

Exemplos com identificador provisório (`ffmpeg-studio`) e versão `1.0.0-rc.1`:
- `ffmpeg-studio-1.0.0-rc.1-linux-x64.rpm`
- `ffmpeg-studio-1.0.0-rc.1-linux-x64.deb`
- `ffmpeg-studio-1.0.0-rc.1-linux-x64.tar.gz`
- `ffmpeg-studio-1.0.0-rc.1-windows-x64.exe`
- `ffmpeg-studio-1.0.0-rc.1-windows-x64.zip`
- `ffmpeg-studio-1.0.0-rc.1-macos-arm64.dmg`
- `ffmpeg-studio-1.0.0-rc.1-macos-x64.dmg`

---

## 3. Estados dos Artefatos

| Estado | Significado |
| :--- | :--- |
| **Planejado** | Definido na estratégia e scripts, ainda não compilado no ambiente alvo |
| **Construído** | Gerado pelo `jpackage` / script com checksum SHA-256 calculado |
| **Testado** | Instalado e executado em sistema operacional alvo |
| **Aprovado** | Validado pelo proprietário para release |
| **Publicado** | Disponibilizado publicamente no GitHub Releases (nenhum na `1.0.0-rc.1` ainda) |
