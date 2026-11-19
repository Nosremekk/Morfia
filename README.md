<p align="center">
  <img src="docs/branding/logo/morfia-logo.svg" alt="Morfia Logo" width="96" height="96"/>
</p>

# Morfia — Multimedia Processing Studio

> **Versão atual / Current version:** `1.0.0-rc.1` (Release Candidate)  
> **Projeto independente:** Morfia utiliza executáveis externos do FFmpeg e FFprobe e não é afiliado ou endossado pelo projeto FFmpeg.

---

## 🇧🇷 Português (Brasil)

### 1. Apresentação
**Morfia** é uma estação de trabalho multimídia de código aberto que oferece uma interface visual para processamento local de vídeo, áudio, imagens e outros arquivos de mídia utilizando FFmpeg.

O objetivo é tornar ferramentas profissionais de processamento multimídia acessíveis sem exigir interação direta com a linha de comando, mantendo operação 100% offline, local-first e sem telemetria.

### 2. Screenshots
Capturas de tela interativas da versão `1.0.0-rc.1` ainda não foram anexadas a este repositório (nenhuma imagem sintética ou fabricada é exibida). A interface adota estética técnica retrô inspirada em estações de trabalho dos anos 1990 e 2000, com tema escuro (`dark-theme.css`) e tema claro (`light-theme.css`).

### 3. Funcionalidades
Consulte a classificação completa em [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md):
- **Estáveis:** Inspeção técnica via FFprobe JSON, conversão MP4/MKV, compressão dedicada com CRF, extração e normalização de áudio EBU R128 (`loudnorm` em 1 ou 2 passagens), cortes rápidos e precisos, GIF animado em 2 passagens, extração periódica de frames, conversão de imagens, concatenação rápida e compatível, legendas (extração, incorporação e burn-in), motor tipado de filtros, gerenciamento de streams e metadados, biblioteca compartilhada, fila persistente com recuperação `INTERRUPTED`, histórico, diagnósticos locais sanitizados, atalhos de teclado e assistente de configuração do FFmpeg em 9 etapas.
- **Parciais / Experimentais:** Preview de vídeo por proxy e forma de onda/reprodução PCM; aceleração por hardware (`VAAPI`) com sondagem real em 3 níveis (dependente do driver e pacote FFmpeg do sistema).

### 4. Plataformas
- **Validado localmente:** Linux `x86_64` (Fedora Linux 44, OpenJDK 25, JavaFX 24).
- **Suporte planejado (não validado em execução nativa local):** Linux Ubuntu/Debian `x86_64`, Windows `x86_64`, macOS `arm64` e `x86_64`. Consulte [docs/MULTIPLATFORM_VALIDATION.md](docs/MULTIPLATFORM_VALIDATION.md).

### 5. Instalação
Na versão `1.0.0-rc.1`, o pacote distribuível Linux gerado localmente é a distribuição portátil `morfia-1.0.0-rc.1-linux-x64.tar.gz` (com runtime Java 25 e JavaFX 24 integrados).
```bash
tar -xzf morfia-1.0.0-rc.1-linux-x64.tar.gz
./morfia/bin/morfia
```
Pacotes `.rpm`, `.deb`, `.exe`, `.msi` e `.dmg` possuem scripts preparados em `packaging/`, mas dependem de ferramentas ou runners específicos não presentes no ambiente local atual.

### 6. Configuração do FFmpeg
**Morfia não inclui nem baixa executáveis do FFmpeg ou FFprobe.** Ao abrir pela primeira vez, utilize o **Assistente FFmpeg** para detectar automaticamente ou apontar manualmente os binários instalados no sistema. Consulte [docs/INSTALLATION.md](docs/INSTALLATION.md).

### 7. Primeiros Passos
1. Abra o Morfia e conclua o **Assistente FFmpeg** (ou clique em *Configurar Depois* para navegar pela interface).
2. Importe arquivos na aba **Biblioteca** ou selecione uma mídia diretamente no **Converter**, **Compressão** ou **Ferramentas**.
3. Envie tarefas para a **Fila de Tarefas** e acompanhe progresso, logs e histórico.

### 8. Build a partir do Código-Fonte
Pré-requisitos: JDK 25+, Maven 3.9+, FFmpeg e FFprobe instalados (para testes de integração).
```bash
mvn clean compile
mvn clean verify
mvn javafx:run
```

### 9. Arquitetura
Arquitetura Hexagonal Simplificada combinada com MVVM (`domain`, `application`, `infrastructure` e `ui`). O domínio permanece isolado de JavaFX e de chamadas diretas ao sistema operacional; processos externos são invocados exclusivamente via `ProcessBuilder` com listas estruturadas (`List<String>`). Consulte [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### 10. Testes
Suíte automatizada executada via `mvn clean verify` (JUnit 5, testes unitários, testes de integração com FFmpeg/FFprobe reais e carregamento de todas as 14 telas FXML no Toolkit JavaFX).

### 11. Contribuições
Consulte [CONTRIBUTING.md](CONTRIBUTING.md) e [AGENTS.md](AGENTS.md) para diretrizes de arquitetura, convenção de commits e regras de segurança.

### 12. Roadmap
O roadmap original planejado encerra-se nesta etapa de preparação da **`v1.0.0-rc.1`**, aguardando validação multiplataforma nos runners nativos e revisão final do proprietário antes da eventual promoção para `v1.0.0`.

### 13. Licença
Distribuído sob a **Licença MIT** (`Copyright (c) 2026 Emerson Vinicius Santos de Santana`). Consulte [LICENSE](LICENSE) e [docs/LICENSING.md](docs/LICENSING.md).

### 14. Avisos de Dependências
Consulte [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) para o inventário completo de dependências de terceiros (OpenJDK 25, JavaFX 24, Jackson, SLF4J, Logback) e a separação jurídica dos executáveis externos FFmpeg/FFprobe.

### 15. Limitações Conhecidas
- Suporte nativo em Windows e macOS ainda não foi validado em hardware/runners correspondentes.
- Instaladores Windows e macOS não possuem assinatura digital Authenticode ou notarização Apple nesta release candidate.
- Aceleração por hardware VAAPI no Linux depende de drivers e encoders disponíveis no FFmpeg do host.

---

## 🇺🇸 English

**Morfia** (*Multimedia Processing Studio*) is an open-source, local-first, 100% offline desktop multimedia workstation for converting, editing, inspecting and processing local media files using external **FFmpeg** and **FFprobe**, built with **Java 25** and **JavaFX 24** (Simplified Hexagonal Architecture + MVVM).

- **No Bundled FFmpeg Binaries:** Users configure local FFmpeg and FFprobe binaries through a 9-step Setup Wizard ([docs/INSTALLATION.md](docs/INSTALLATION.md)).
- **Platforms:** Locally validated on Fedora Linux `x86_64`; Windows `x86_64` and macOS `arm64`/`x86_64` are planned and pending native runner validation ([docs/MULTIPLATFORM_VALIDATION.md](docs/MULTIPLATFORM_VALIDATION.md)).
- **Build & Test:** `mvn clean verify` and `mvn javafx:run`.
- **License:** [MIT License](LICENSE) (`Copyright (c) 2026 Emerson Vinicius Santos de Santana`). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
