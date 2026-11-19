# Motor de Filtros e Pré-visualização — FFmpeg Studio

O **FFmpeg Studio** implementa um motor de filtros fortemente tipado (`com.ffmpegstudio.domain.model.filter`), impedindo a injeção de strings arbitrárias vindas da interface gráfica e garantindo composição determinística de grafos de filtro.

---

## 1. Arquitetura Tipada (`MediaFilter`)

Todos os filtros implementam a interface `MediaFilter` e são divididos em `VideoFilters` (`VideoFilter`) e `AudioFilters` (`AudioFilter`):

### Filtros Geométricos (`GEOMETRIC`)
- **`Resize`**: `scale=W:-2` (preservando proporção) ou `scale=W:H` (dimensões pares garantidas para `yuv420p`).
- **`Crop`**: `crop=W:H:X:Y` com validação geométrica cumulativa em `FilterGraphBuilder`.
- **`Pad`**: `pad=W:H:(ow-iw)/2:(oh-ih)/2:color`.
- **`Rotate`**: `transpose=1` (90° CW), `transpose=2` (90° CCW), `transpose=2,transpose=2` (180°).
- **`Flip`**: `hflip` e/ou `vflip`.
- **`AspectRatio`**: `setdar=W/H`.

### Filtros de Imagem (`IMAGE`)
- **`ColorAdjust`**: `eq=brightness=..:contrast=..:saturation=..:gamma=..`.
- **`Grayscale`**: `hue=s=0`.
- **`Sharpen`**: `unsharp=5:5:amount:5:5:0.0`.
- **`Blur`**: `gblur=sigma=..`.
- **`Denoise`**: `hqdn3d=..`.
- **`Deinterlace`**: `yadif`.

### Filtros Temporais (`TEMPORAL`)
- **`ChangeFps`**: `fps=..`.
- **`Speed`**: `setpts=(1/multiplier)*PTS` (com recálculo da duração estimada).
- **`VideoFade`**: `fade=t=in|out:st=..:d=..`.
- **`Reverse`**: `reverse` — emite **aviso explícito de consumo de memória RAM** (especialmente acima de 30s).

### Filtros de Áudio (`AUDIO`)
- **`Volume`**: `volume=..`.
- **`AudioFade`**: `afade=t=in|out:st=..:d=..`.
- **`AudioSpeed`**: encadeamento automático de múltiplos estágios `atempo` para fatores fora de `[0.5, 2.0]` (ex.: `0.25x -> atempo=0.5,atempo=0.5`).
- **`Equalizer`**: `bass`, `equalizer` (1000 Hz) e `treble`.
- **`Loudnorm`**: `loudnorm=I=-16:TP=-1.5:LRA=11` (passagem única).
- **`Channels`**: `aformat=channel_layouts=mono|stereo`.

---

## 2. Composição e Validação (`FilterGraphBuilder`)

- Os filtros de vídeo são compostos em uma **única** flag `-vf` separados por vírgula (nunca múltiplas flags `-vf`).
- Os filtros de áudio são compostos em uma **única** flag `-af` separados por vírgula.
- `FilterGraphBuilder.validate(...)` rastreia alterações dimensionais sequenciais (ex.: `Resize` seguido de `Crop` ou `Rotate` seguido de `Crop`), verifica disponibilidade no `ffmpeg -filters` local e valida faixas de áudio/vídeo existentes.

---

## 3. Serviço Assíncrono de Pré-visualização (`FilterPreviewService`)

- Extrai apenas o frame do timestamp selecionado (`-ss <t> -i <arquivo> -frames:v 1`) para PNG, sem carregar o vídeo em memória.
- Gera comparativo **Antes vs Depois** com *debounce* de 250 ms na UI e descarte automático de requisições obsoletas (`AtomicLong latestRequestId`).
- Cache temporário limitado (`MAX_CACHE_ENTRIES = 16`) com remoção automática de arquivos antigos e limpeza via `clearCache()`.
- Filtros temporais (`isTemporal() == true`) exibem aviso informando a limitação da prévia em um único frame estático.
