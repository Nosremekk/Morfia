# Motor de Pré-visualização e Reprodução — FFmpeg Studio

---

## 1. Análise de Alternativas de Reprodução

| Alternativa | Vantagens | Desvantagens / Impacto | Decisão |
| :--- | :--- | :--- | :--- |
| `javafx.scene.media.MediaPlayer` | Integração nativa JavaFX | Requer módulo `javafx-media` e plugins GStreamer no Linux; falha com MKV/HEVC/FLAC/WebM sem codecs GStreamer extras. | Não adotada como dependência obrigatória |
| **FFmpeg Frames + Proxy + JDK `SourceDataLine`** | Zero dependências extras, suporte universal a qualquer formato decodificável pelo FFmpeg, sem carregar vídeos completos em RAM. | Requer invocação assíncrona de subprocessos FFmpeg para proxies/frames. | **Adotada** |
| Biblioteca externa (VLCJ / JavaCV) | Reprodução universal direta | Alto peso de empacotamento, bibliotecas nativas externas e complexidade de licenciamento. | Descartada |

---

## 2. Componentes Implementados

- **`FilterPreviewService`**:
  - Extrai frame único por timestamp (`-ss <t> -i <arquivo> -frames:v 1`) para PNG.
  - Comparativo Antes vs Depois com *debounce* de 250 ms e descarte de requisições obsoletas (`AtomicLong`).
  - Cache LRU limitado (`MAX_CACHE_ENTRIES = 16`).
  - Aviso explícito quando há filtros temporais (`isTemporal()`).
- **`MediaPreviewService`**:
  - **Proxy de Baixa Resolução**: Gera MP4 640px (`libx264 ultrafast`, `aac`) cancelável com cache limitado (`MAX_PROXY_CACHE = 4`), removido em `shutdown()`.
  - **Forma de Onda (Waveform)**: Agrega picos normalizados `[0.0..1.0]` lendo PCM mono 8kHz diretamente do `stdout` (`pipe:1`) sem carregar o áudio inteiro em memória.
  - **Reprodução de Áudio**: Streaming PCM estéreo 44.1kHz para `javax.sound.sampled.SourceDataLine` com controle de ganho e liberação imediata em `stopAudio()` / `shutdown()`.
