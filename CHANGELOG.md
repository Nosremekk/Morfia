# Changelog

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.
O formato segue o padrão [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e [Semantic Versioning](https://semver.org/).

## [1.0.0-rc.1] — Fase 6.16 (Identidade Morfia, Empacotamento e Release Candidate)

### Adicionado
- **Identidade Definitiva (`Morfia`)**: Consolidação da marca Morfia (`AppIdentity.java`), paleta retrô inspirada em estações multimídia dos anos 1990/2000 (`dark-theme.css`), logotipo SVG original (`docs/branding/logo/morfia-logo.svg`), PNGs (16–1024px) e `morfia.ico` em `packaging/icons/`.
- **Decisões do Proprietário (`docs/decisions/OWNER_DECISIONS.md`)**: Registro formal de KD-001 a KD-009.
- **Camada Multiplataforma (`infrastructure/platform`)**: `PlatformInfo`, `PlatformDirectories` (XDG Linux, `APPDATA`/`LOCALAPPDATA` Windows, `~/Library/*` macOS + migração não destrutiva e idempotente de `~/.config/ffmpeg-studio` para `~/.config/morfia`), `ExecutableResolver`, `DesktopIntegrationService` e `ProcessLifecycleService`.
- **Assistente de Configuração FFmpeg (`SetupWizardController` / `setup-wizard.fxml`)**: Fluxo guiado em 9 etapas com detecção do SO, busca automática, verificação de versões/capacidades, seleção manual, instruções por plataforma, teste final e seção Sobre/Licenciamento; permite abertura do app sem FFmpeg desabilitando apenas operações dependentes.
- **Licenciamento e Governança**: `LICENSE` (MIT, `Copyright (c) 2026 Emerson Vinicius Santos de Santana`), `THIRD_PARTY_NOTICES.md`, `CONTRIBUTING.md`, `SECURITY.md`, `docs/FEATURE_MATRIX.md`, `docs/releases/1.0.0-rc.1.md`, templates GitHub e workflows multiplataforma.
- **Artefatos Linux (`x86_64`)**: Geração de `app-image`, `morfia-1.0.0-rc.1-linux-x64.tar.gz`, `morfia-1.0.0-0.1.rc1.x86_64.rpm` e `morfia_1.0.0-0.1.rc1_amd64.deb` via `jpackage` com runtime Java 25 integrado, validação funcional em ambiente isolado e checksums SHA-256.
- **CI, Windows e SBOM (Fase 6.17)**: Execução verde do workflow `CI Multiplatform` em Linux `x86_64`, Windows `x86_64` e macOS `arm64`; geração de `.zip`, `.exe` e `.msi` Windows (`0.99.1`) e `.deb`/`.tar.gz` Ubuntu via GitHub Actions; geração de SBOM CycloneDX (`bom.json`/`bom.xml`).

### Anteriormente (Fases 1 a 5)
- **Fase 5**: Tema claro/escuro ao vivo, internacionalização (`pt_BR`/`en`), Workspace de mídia compartilhado, proxy de vídeo e forma de onda/reprodução de áudio PCM, fila persistente com recuperação `INTERRUPTED`, histórico, classificação de erros, diagnóstico sanitizado, normalização `loudnorm` em 2 passagens, extração periódica de frames, conversão de imagens e proteção de RAM no filtro `reverse`.
- **Fase 4**: Concatenação rápida/compatível, gerenciamento e queima de legendas, motor tipado de filtros, pré-visualização Antes/Depois, gerenciamento de streams/metadados e sondagem de aceleração por hardware em 3 níveis.
- **Fase 3**: Compressão dedicada, ferramentas de áudio, cortes, GIFs em 2 passagens, extração de frames, fila concorrente e sistema de presets JSON.
- **Fases 1–2**: Arquitetura hexagonal + MVVM, detecção de FFmpeg/FFprobe, inspeção JSON via FFprobe, pipeline de transcodificação e monitor de progresso.
