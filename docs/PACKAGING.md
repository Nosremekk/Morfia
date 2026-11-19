# Guia de Empacotamento Multiplataforma (`PACKAGING.md`)

O empacotamento utiliza `jpackage` (JDK 25) para gerar distribuições autocontidas com runtime Java e JavaFX, **sem incluir executáveis FFmpeg/FFprobe**.

---

## 1. Mapeamento de Versão SemVer -> Empacotadores Nativos

- Versão do produto (SemVer): **`1.0.0-rc.1`**
- Empacotadores nativos (`RPM`, `DEB`, `MSI`, `EXE`, `DMG`) no `jpackage` exigem formato estritamente numérico (`MAJOR.MINOR.PATCH`, ex.: **`1.0.0`**) no argumento `--app-version`.
- Por isso:
  - Os **nomes dos arquivos distribuíveis** usam o identificador SemVer completo: `<produto>-1.0.0-rc.1-<sistema>-<arquitetura>.<extensão>`.
  - Os **metadados internos** do `jpackage` usam `1.0.0` (enquanto `AppIdentity.VERSION` exibe `1.0.0-rc.1` na interface e diagnósticos).

---

## 2. Linux (`x86_64`)

Script: [`packaging/linux/package-linux.sh`](../packaging/linux/package-linux.sh)
- **Artefatos planejados:** `.tar.gz` portátil, `app-image`, `.rpm` (Fedora/RHEL via `rpmbuild`), `.deb` (Debian/Ubuntu via `dpkg-deb`).
- **Integração desktop:** Grupo de menu `AudioVideo` (`--linux-menu-group "AudioVideo"`) e atalho `.desktop` (`--linux-shortcut`).
- **Diretórios:** Segue padrões XDG (`XDG_CONFIG_HOME`, `XDG_CACHE_HOME`) preservando `~/.config/ffmpeg-studio`.

---

## 3. Windows (`x86_64`)

Script: [`packaging/windows/package-windows.ps1`](../packaging/windows/package-windows.ps1)
- **Artefatos planejados:** `.zip` portátil, `app-image`, `.exe` / `.msi`.
- **Requisitos de WiX:** A geração de `.exe` e `.msi` via `jpackage` no JDK 25 exige o **WiX Toolset** (WiX v3.11+ ou WiX v4/v5 conforme suporte do `jpackage` do JDK instalado no runner Windows) presente no `PATH`.
- **Limitação de assinatura digital:** Sem certificado Authenticode (EV/OV), o Windows SmartScreen exibirá aviso de editor desconhecido em downloads públicos. **Não** instruímos usuários a desabilitar o SmartScreen.

---

## 4. macOS (`arm64` e `x86_64`)

Script: [`packaging/macos/package-macos.sh`](../packaging/macos/package-macos.sh)
- **Artefatos planejados:** `.app` (`app-image`) e `.dmg` específicos por arquitetura (`arm64` e `x86_64`). **Não** há suporte a Universal Binary nesta release.
- **Gatekeeper, Code Signing e Notarização:**
  - Distribuição pública no macOS exige assinatura com **Developer ID Application** (`codesign`) e **notarização Apple** (`notarytool` / conta Apple Developer paga).
  - Sem assinatura e notarização Apple, o Gatekeeper bloqueará a execução direta por usuários finais ao baixar da internet. **Não** instruímos usuários a desabilitar o Gatekeeper globalmente (`spctl --master-disable`).
  - Maturidade atual do macOS: **Planejado / Em validação de CI (sem assinatura/notarização Apple configurada)**.
