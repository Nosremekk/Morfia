# Auditoria Técnica — Fases 1 a 4 (Pré-Fase 5)

**Data da Auditoria**: 2026-11-18  
**Escopo**: Inspeção da árvore real do projeto, execução de `mvn clean verify`, verificação de relatórios XML do Maven Surefire, verificação de carregamento de todos os arquivos FXML no JavaFX 24, revisão de controle de versão Git e identificação de pendências técnicas e arquiteturais para a Fase 5.

---

## 1. Contagem Real de Testes (Pré-Fase 5)

Execução de `mvn clean verify` finalizada com `BUILD SUCCESS` (`exit code 0`).

- **Classes de teste**: 27 (antes de `FxmlLoadingTest`)
- **Total de testes**: **78** (**0 falhas, 0 erros, 0 ignorados**)
- **Carregamento FXML (`FxmlLoadingTest`)**: **11 de 11 arquivos FXML** (`main-layout.fxml`, `dashboard.fxml`, `converter.fxml`, `compression.fxml`, `tools.fxml`, `concat.fxml`, `subtitles.fxml`, `filters.fxml`, `streams-metadata.fxml`, `queue.fxml`, `settings.fxml`) carregados com sucesso no `JavaFX Application Thread` real (`JavaFX 24`, `WAYLAND=wayland-0`).

---

## 2. Controle de Versão (Git)

- O diretório `/home/vini/ffmpeg-visual` corresponde à raiz do projeto e já possui um repositório `.git` inicializado (sem repositório ancestral conflitante).
- `.gitignore` foi expandido para cobrir artefatos Maven/Java (`target/`, `*.class`, `*.jar`), logs, arquivos temporários (`*.~tmp_*`, `hs_err_pid*`), caches locais (`.ffmpegstudio-cache/`), configurações pessoais (`settings.json`, `presets.json`, `queue.json`, `history.json`), IDEs e mídias utilizadas em testes manuais.

---

## 3. Problemas, Duplicações e Pendências Identificadas

1. **Divergência VA-API (Fase 5.1)**:
   - `h264_vaapi` e `hevc_vaapi` funcionaram diretamente no terminal do host (`27.2x` / `29.5x`), mas falharam dentro do Surefire com `No usable encoding profile found` (`exit=218`). Requer reprodução mínima isolada via `ProcessBuilder` comparando ambiente, executável, variáveis e contexto Java.
2. **Normalização `loudnorm` em Passagem Única (Fase 5.1)**:
   - `AudioToolsOperation` e `AudioFilters.Loudnorm` executam apenas passagem única dinâmica, sem modo de duas passagens (análise + aplicação com métricas medidas).
3. **Extração Periódica de Frames e Conversão de Imagens (Fase 5.1)**:
   - `ExtractFramesOperation` suporta apenas captura única (`-frames:v 1`). Falta extração por intervalo/FPS e conversão isolada entre formatos de imagem (PNG, JPEG, WebP, BMP, TIFF).
4. **Proteção de Memória no Filtro `reverse` (Fase 5.1)**:
   - `FilterGraphBuilder` verifica apenas `duration > 30s`, sem estimar consumo de RAM descomprimida com base em largura, altura, FPS e profundidade de bits.
5. **Detecção Repetida de Capacidades na Inicialização da UI (Fase 5.2 / 5.9)**:
   - Logs do `FxmlLoadingTest` revelaram que múltiplos ViewModels invocam `DetectCapabilitiesService.getCapabilities("ffmpeg")` simultaneamente na inicialização, disparando subprocessos `ffmpeg -version/-encoders/-filters` redundantes.
6. **Inconsistência Visual, Estilos Inline e Mistura de Idiomas (Fase 5.2 / 5.7)**:
   - Telas das Fases 1–2 (`dashboard.fxml`, `converter.fxml`, `settings.fxml`) estão em inglês enquanto telas das Fases 3–4 estão em português, além de estilos inline repetidos nos arquivos FXML e ausência de `ResourceBundle`.
7. **Ausência de Área de Trabalho Compartilhada, Persistência de Fila/Histórico e Encerramento Seguro (Fases 5.3, 5.5, 5.8)**:
   - Arquivos importados não são compartilhados entre telas; a fila existe apenas em memória; o histórico não é persistido; e o fechamento da janela não intercepta tarefas ativas nem garante limpeza coordenada de executores/proxies.
