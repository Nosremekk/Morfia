# Funcionalidades & Roadmap — FFmpeg Studio

O desenvolvimento do **FFmpeg Studio** segue um cronograma incremental focado em robustez, usabilidade e qualidade técnica.

---

## 📌 Fase 1 — Fundação do Projeto (Status: CONCLUÍDA)

- [x] **Setup do Projeto**: Maven com Java 25 e JavaFX 24.
- [x] **Arquitetura Hexagonal & MVVM**: Separação estrita entre `domain`, `application`, `infrastructure` e `ui`.
- [x] **Tema Escuro Moderno**: Interface profissional no estilo desktop workstation, paleta escura refinada, tipografia limpa e responsiva.
- [x] **Sistema de Navegação**: Sidebar com troca instantânea de telas e indicador de status do engine.
- [x] **Detecção de FFmpeg & FFprobe**:
  - Detecção automática de binários no `$PATH` do sistema operacional.
  - Extração de versão real do FFmpeg e FFprobe.
  - Análise de acelerações de hardware suportadas (CUDA, VAAPI, Vulkan, QSV, etc.).
  - Varredura de encoders de vídeo, encoders de áudio e filtros de mídia.
- [x] **Tela de Configurações**:
  - Definição customizada de caminhos de executáveis com botão "Browse..." e "Auto-detect".
  - Teste em tempo real de binários com badges de diagnóstico (sucesso/erro).
  - Inspetor visual de capacidades dividido em abas (Hardware, Encoders de Vídeo, Encoders de Áudio, Filtros).
  - Configuração de diretório padrão de saída e limite de concorrência.
  - Persistência automática em JSON (`~/.config/ffmpeg-studio/settings.json`).
- [x] **Dashboard de Boas-Vindas**:
  - Cartões de resumo com contadores de encoders, aceleração e filtros.
  - Área visual de importação de arquivos por Drag & Drop.
- [x] **Testes Automatizados**:
  - Testes unitários para parsers, sistema de arquivos, repositório de configurações e executor de processos.
  - Testes de integração com validação de binários reais do sistema.

---

## 📌 Fase 2 — Motor Multimídia e Pipeline de Transcodificação (Status: CONCLUÍDA)

- [x] **Inspeção Estruturada via FFprobe**:
  - Parser robusto de saída JSON do FFprobe (`-print_format json -show_format -show_streams -show_chapters`).
  - Extração detalhada de contêiner, duração, bitrate global, tags e metadados.
  - Inspeção de faixas de vídeo (resolução, aspect ratio calculado/declarado, FPS real e nominal fracionário, pixel format, bitrate).
  - Inspeção de faixas de áudio (codec, sample rate em Hz, canais, channel layout, bitrate, idioma).
  - Inspeção de legendas (codec, título, idioma, flags default/forced) e capítulos.
  - Resiliência a campos ausentes, valores `N/A`, durações desconhecidas e arquivos corrompidos.
- [x] **Pipeline de Transcodificação Imutável**:
  - Modelos de domínio imutáveis: `ConversionSettings`, `ContainerFormat` (MP4, MKV), `VideoCodecOption` (libx264, libx265, stream copy, none), `AudioCodecOption` (AAC, stream copy, none), `PresetSpeed`, `ResolutionPreset` (Original, 1080p, 720p, 480p, custom).
  - `ConversionValidator`: Validação rigorosa de compatibilidade entre codecs, contêineres, limites de CRF (0-51) e verificação de encoders reais disponíveis no FFmpeg local.
- [x] **FFmpegCommandBuilder**:
  - Geração determinística de argumentos estruturados para `ProcessBuilder` (sem concatenação de strings ou shell).
  - Separação de argumentos globais, de entrada, mapeamento de streams (`-map`), filtros de vídeo (`-vf scale`, `-vf fps`), codecs de vídeo/áudio e flags de contêiner (`+faststart`).
  - Método `toDisplayString()` para inspeção do comando na interface gráfica.
- [x] **Gerenciamento de Processos & Progresso em Tempo Real**:
  - `FFmpegProcessExecutor.executeStreaming()` com captura assíncrona de `stdout` e `stderr`.
  - Parser do protocolo `-progress pipe:1` para cálculo de porcentagem precisa, FPS instantâneo, bitrate, velocidade de conversão e ETA. Suporte a progresso indeterminado para durações desconhecidas.
  - Cancelamento controlado com encerramento de árvore de processos filhos (`ProcessHandle.descendants()`).
- [x] **Segurança e Integridade de Arquivos**:
  - Transcodificação com saída em arquivo temporário isolado (`.~tmp_*`).
  - Renomeação atômica apenas após sucesso comprovado (exit code 0).
  - Exclusão garantida do arquivo temporário em caso de erro ou cancelamento pelo usuário.
  - Blindagem total contra sobrescrita acidental de mídias originais.
- [x] **Interface Gráfica do Transcodificador**:
  - Nova tela `Converter` integrada à navegação lateral.
  - Seleção de arquivos via FileChooser ou Drag & Drop (tanto no Dashboard quanto no Converter).
  - Exibição de cards de metadados da mídia selecionada.
  - Ajustes de contêiner, codecs, slider de CRF com valor em tempo real, preset e resolução.
  - Pré-visualização do comando FFmpeg equivalente.
  - Barra de progresso com métricas em tempo real (%, FPS, Speed, ETA) e console de logs técnicos expansível.
  - Botão de cancelamento responsivo a qualquer instante.
- [x] **34 Testes Automatizados**:
  - Testes unitários para parsing JSON, cálculo fracionário de FPS, validação de regras, máquina de estados `ProcessingJob` e parser de progresso.
  - Testes de integração de ponta a ponta gerando mídia sintética no Fedora, executando transcodificação real para MKV, inspecionando arquivo resultante e testando cancelamento limpo.

---

## 📌 Fase 3 — Suíte de Ferramentas Multimídia e Fila em Lote (Status: CONCLUÍDA)

- [x] **Abstração Unificada de Operações (`MediaOperation`)**:
  - Interface polimórfica independente de JavaFX que padroniza validação, nomenclatura, estimativa de saída e geração determinística de argumentos FFmpeg.
  - Operações implementadas: `TranscodeOperation`, `CompressOperation`, `AudioToolsOperation`, `TrimOperation`, `GenerateGifOperation` e `ExtractFramesOperation`.
  - Desacoplamento completo entre UI e construção de comandos.
- [x] **Tela Dedicada de Compressão (`CompressionController` & `compression.fxml`)**:
  - Seleção por FileChooser e Drag & Drop.
  - Perfis predefinidos de compressão: Leve (alta qualidade), Equilibrado (recomendado) e Agressivo (tamanho mínimo).
  - Slider intuitivo de Fator de Taxa Constante (CRF) com badges dinâmicos de valor.
  - Seletor de codecs (H.264 / H.265 detectado dinamicamente), presets de velocidade e redimensionamento de resolução.
  - Callout informativo explicando que a codificação baseada em CRF não garante tamanho exato em MB, alocando bitrate conforme complexidade de cena.
  - Comparativo pós-processamento: tamanho original vs tamanho comprimido com percentual de redução calculado e botão "Abrir Pasta".
- [x] **Ferramentas Especializadas de Áudio (`ToolsController` - Aba Áudio)**:
  - Extração e conversão de áudio para MP3 (libmp3lame), AAC, FLAC (lossless) e WAV (uncompressed PCM).
  - Controle de ganho de volume (slider de 25% a 200%).
  - Normalização profissional EBU R128 (`loudnorm`) em conformidade com padrões de radiodifusão e streaming.
  - Mixdown de canais (Mono 1.0, Estéreo 2.0 ou manter original) e conversão de sample rate (44.1 kHz, 48.0 kHz).
  - Seleção de trilha de áudio individual para contêineres multi-faixa.
- [x] **Corte Preciso de Vídeo (`ToolsController` - Aba Corte)**:
  - Definição de timestamps de início e fim com cálculo automático da duração resultante.
  - Alternância entre **Corte Rápido** (Stream Copy sem recodificação `-c copy`, instantâneo nos keyframes) e **Corte Preciso** (recodificação quadro a quadro com precisão milimétrica).
  - Callout explicativo na interface para orientação do usuário.
- [x] **Imagens e GIFs Animados (`ToolsController` - Aba Imagens & GIFs)**:
  - Criação de GIFs animados de alta qualidade utilizando pipeline de duas passagens com geração de paleta personalizada (`palettegen` / `paletteuse`).
  - Configuração de FPS, largura e duração.
  - Extração de frames estáticos de alta resolução (PNG / JPEG) em timestamps arbitrários.
- [x] **Fila de Processamento em Lote (`JobQueueService` & `queue.fxml`)**:
  - Execução concorrente gerenciada com limite máximo definido pelas configurações globais (`AppSettings.maxConcurrentJobs`).
  - Adição de arquivos individuais ou seleção múltipla com aplicação de preset de lote.
  - Botões para Iniciar, Pausar, Cancelar Individualmente, Cancelar Tudo e Limpar Concluídas.
  - Reordenação dinâmica de tarefas pendentes (Mover para Cima / Baixo).
  - Barra de progresso agregada em tempo real com resumo estatístico.
- [x] **Sistema de Presets Persistentes em JSON (`JsonPresetRepository` & `PresetService`)**:
  - Repositório local em `~/.config/ffmpeg-studio/presets.json`.
  - 8 Presets de fábrica embutidos com proteção contra alteração/remoção indevida.
  - Criação, edição e exclusão de presets customizados do usuário.
  - Funcionalidade completa de exportação e importação de bibliotecas de presets em arquivo JSON.
  - Seletor de presets integrado com um clique no Converter e na Fila.
- [x] **55 Testes Automatizados (100% de Sucesso)**:
  - Testes unitários para todas as novas operações (`CompressOperation`, `AudioToolsOperation`, `TrimOperation`, `GenerateGifOperation`, `ExtractFramesOperation`).
  - Testes unitários para persistência de presets e controle concorrente da fila.
  - Testes de integração de ponta a ponta com FFmpeg real: compressão, extração de MP3 com loudnorm, corte de vídeo, geração de GIF e fila de processamento em lote.

---

## 📌 Fase 4 — Ferramentas Avançadas, Filtros, Streams e Aceleração por Hardware (Status: CONCLUÍDA)

- [x] **4.0 Auditoria das Fases Anteriores**:
  - Auditoria documentada em [`docs/audits/PHASE_3_AUDIT.md`](audits/PHASE_3_AUDIT.md).
  - Correção de colisões de arquivos temporários (`ProcessingJob.create`) e validação de colisões de destino em `JobQueueService`.
  - Remoção de afirmações enganosas de redução percentual fixa para compressão por CRF.
- [x] **4.1 Concatenação de Mídias (`ConcatOperation` & `ConcatController`)**:
  - Concatenação Rápida (`concat` demuxer sem recodificação) com validação prévia de compatibilidade e escaping de caminhos.
  - Concatenação Compatível (`concat` filter) com normalização de resolução, FPS, pixel format, áudio e geração de silêncio (`anullsrc`) para clipes sem áudio.
  - Limpeza garantida do arquivo temporário de lista (`.~tmp_concat_*.txt`).
- [x] **4.2 Gerenciamento de Legendas (`SubtitleOperation` & `SubtitlesController`)**:
  - Extração, incorporação selecionável (soft-embed), queima (*burn-in* com `subtitles`/`ass`), remoção e edição de idioma/título/flags (`default`, `forced`) e deslocamento temporal (`-itsoffset`).
  - Diferenciação explícita entre legendas textuais (`SRT`, `ASS`, `SSA`, `WEBVTT`, `MOV_TEXT`) e gráficas (`PGS`, `DVD_SUBTITLE`), bloqueando conversões sem OCR.
- [x] **4.3 Motor de Filtros Tipado (`FilterGraphBuilder` & `FilterOperation`)**:
  - Filtros geométricos (`Resize`, `Crop`, `Pad`, `Rotate`, `Flip`, `AspectRatio`), de imagem (`ColorAdjust`, `Grayscale`, `Sharpen`, `Blur`, `Denoise`, `Deinterlace`), temporais (`ChangeFps`, `Speed`, `VideoFade`, `Reverse` com aviso de memória) e de áudio (`Volume`, `AudioFade`, `AudioSpeed` com encadeamento `atempo`, `Equalizer`, `Loudnorm`, `Channels`).
  - Composição única em `-vf` / `-af` com validação de dependências, dimensões acumuladas e duração estimada.
- [x] **4.4 Pré-visualização Visual de Filtros (`FilterPreviewService`)**:
  - Extração assíncrona de frames Antes/Depois sem carregar vídeos completos em memória, com *debounce* de 250 ms, descarte de requisições obsoletas, cache limitado e aviso para filtros temporais.
- [x] **4.5 e 4.6 Gerenciamento de Streams e Metadados (`StreamOperation`, `MetadataOperation` & `StreamsMetadataController`)**:
  - Inspeção de streams de vídeo, áudio, legenda, capítulos e streams `data`/`attachment` (`OtherStream`) usando índices globais do FFprobe (`-map 0:<globalIndex>`).
  - Seleção, reordenação, edição de título/idioma/disposição padrão e remuxing via stream copy.
  - Edição e limpeza seletiva ou global de metadados preservando capítulos quando configurado, sem falsas garantias de anonimização forense.
- [x] **4.7 Aceleração por Hardware (`HardwareAccelerationService`)**:
  - Verificação em 3 níveis (backend compilado, encoder compilado, encoder funcional em hardware real com teste sintético 256x256, timeout de 5s e inspeção `/dev/dri`).
  - Suporte a `h264_vaapi` e `hevc_vaapi` em `FFmpegCommandBuilder`, sem fallback silencioso.
- [x] **4.8 Interface e Integração**:
  - Novas telas integradas à navegação lateral e conectadas ao motor compartilhado e à fila de processamento.

---

## 📌 Fase 5 — Produtividade, UX, Persistência e Refinamento (Status: CONCLUÍDA)

- [x] **5.0 Auditoria e Controle de Versão**:
  - Auditoria da Fase 4 documentada em [`docs/audits/PHASE_4_AUDIT.md`](audits/PHASE_4_AUDIT.md).
  - `.gitignore` estruturado para Java, Maven, JavaFX, IDEs, logs, arquivos temporários, configurações pessoais e mídias de teste manual.
  - Teste de carregamento real de todas as 13 telas FXML no JavaFX Toolkit (`FxmlLoadingTest`).
- [x] **5.1 Resoluções Técnicas da Auditoria**:
  - Investigação de VAAPI documentada em [`docs/HARDWARE_ACCELERATION.md`](HARDWARE_ACCELERATION.md) (diferença entre runtime Flatpak com `codecs-extra` e FFmpeg nativo do host Fedora).
  - Normalização EBU R128 (`AudioToolsOperation`) com suporte a passagem única dinâmica e dupla passagem linear (`LoudnormMetrics` + `LoudnormMetricsParser` + fallback para passagem única).
  - Extração periódica de frames (`PeriodicFramesOperation`) por intervalo ou FPS com limite de segurança e verificação de colisão de arquivos.
  - Conversão dedicada de imagens (`ImageConversionOperation`: PNG, JPEG, WEBP, BMP, TIFF, redimensionamento, qualidade e preservação de canal alfa).
  - Proteção de memória no filtro `reverse` (`FilterGraphBuilder`): estimativa conservadora de RAM descomprimida com bloqueio acima de 4 GB ou 120s.
  - Cache de capacidades em `DetectCapabilitiesService` com invalidação explícita ao alterar binários.
- [x] **5.2 Sistema de Design, Temas e Componentes (`UiComponents`, `dark-theme.css`, `light-theme.css`)**:
  - Alternância ao vivo entre tema Escuro e Claro, estados `:disabled` / `:focused`, hints de atalhos e componentes reutilizáveis (`dropZone`, `emptyState`, `statusBadge`).
- [x] **5.3 Workspace de Mídia Compartilhado (`MediaLibraryService` & `workspace.fxml`)**:
  - Importação por arquivo, pasta ou drag-and-drop, filtro por tipo/busca, ordenação, detecção de arquivo ausente/modificado no disco, remoção apenas da referência em memória e encaminhamento rápido para ferramentas.
- [x] **5.4 Pré-visualização, Proxy e Áudio (`MediaPreviewService`)**:
  - Geração cancelável de proxy de vídeo (640px) em cache limitado, extração de forma de onda PCM 8kHz mono em streaming sem carregar arquivos inteiros em RAM e reprodução via `javax.sound.sampled.SourceDataLine`.
- [x] **5.5 Persistência da Fila e Histórico (`JsonQueueHistoryRepository`, `history.fxml`)**:
  - Persistência JSON em `~/.config/ffmpeg-studio/queue-history.json` com lista fechada de tipos permitidos (`JobHistoryEntry.ALLOWED_OPERATION_TYPES`), recuperação de tarefas interrompidas (`JobStatus.INTERRUPTED`) no reinício, reenvio, descarte e duplicação de tarefas.
- [x] **5.6 Produtividade e Atalhos**:
  - Atalhos de teclado globais (`Ctrl+O`, `Ctrl+Shift+O`, `Ctrl+F`, `Ctrl+,`, `Escape`), duplicação de jobs e encaminhamento de mídia entre Workspace e telas de processamento.
- [x] **5.7 Configurações Expandidas e Internacionalização (`I18n`)**:
  - Suporte a `pt_BR` e `en` (`messages_pt_BR.properties`, `messages_en.properties`), migração retrocompatível de `AppSettings` (schema v2), diretório temporário, limites de cache/log e confirmações.
- [x] **5.8 Classificação de Erros, Diagnóstico Local e Encerramento Seguro**:
  - `FailureClassifier` para mensagens acionáveis ao usuário, `DiagnosticsService` para relatório local sanitizado (caminhos opt-in) e diálogo de confirmação ao fechar com tarefas em andamento.
- [x] **5.10 Verificação e Documentação**:
  - **101 testes automatizados** em 30 classes de teste (`mvn clean verify`: 0 falhas, 0 erros, 0 ignorados).
  - Guias técnicos em `docs/UI_DESIGN_SYSTEM.md`, `docs/PERSISTENCE.md`, `docs/PREVIEW_ENGINE.md`, `docs/TROUBLESHOOTING.md`, `docs/MANUAL_TEST_PLAN.md` e `docs/RELEASE_READINESS.md`.
