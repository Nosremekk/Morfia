# Padrões e Convenções de Comandos FFmpeg

O **FFmpeg Studio** adota convenções estruturadas e determinísticas para invocar o FFmpeg e o FFprobe.

---

## 1. Princípios de Execução

1. **Argumentos Isolados**:
   Cada flag e valor constitui um elemento único em `List<String>`. Exemplo:
   ```java
   List<String> command = List.of(
       "ffmpeg",
       "-nostdin",
       "-hide_banner",
       "-y",
       "-i", inputPath.toString(),
       "-progress", "pipe:1",
       "-nostats",
       "-map", "0",
       "-c:v", "libx264",
       "-preset", "medium",
       "-crf", "23",
       "-pix_fmt", "yuv420p",
       "-c:a", "aac",
       "-b:a", "192k",
       "-movflags", "+faststart",
       tempOutputPath.toString()
   );
   ```

2. **Drenagem Assíncrona e Notificação de Progresso**:
   Tanto `stdout` (com `-progress pipe:1`) quanto `stderr` (com mensagens técnicas e diagnóstico) são lidos em threads paralelas independentes para que nenhum buffer de pipe do kernel do Linux atinja o limite (normalmente 64 KB), o que congelaria o processo indefinitivamente.

3. **Cancelamento Gracioso & Limpeza**:
   Em caso de cancelamento pelo usuário:
   - Os processos descendentes (`ProcessHandle.descendants()`) são terminados.
   - O processo principal recebe `destroy()` e, caso necessário após um período de tolerância, `destroyForcibly()`.
   - O arquivo temporário de saída (`.~tmp_*`) é imediatamente removido, garantindo que o disco não retenha lixo de processos incompletos e que o arquivo original permaneça intacto.

---

## 2. Comandos de Inspeção e Detecção (Fase 1 e 2)

### Inspeção Estruturada com FFprobe
```bash
ffprobe -v quiet -print_format json -show_format -show_streams -show_chapters input.mp4
```
*Saída analisada*: JSON estruturado contendo streams de vídeo, áudio, legendas, tags, metadados globais e capítulos.

### Detecção de Versão e Capacidades
```bash
ffmpeg -version
ffprobe -version
ffmpeg -hwaccels
ffmpeg -encoders
ffmpeg -decoders
ffmpeg -filters
ffmpeg -formats
```

---

## 3. Comandos de Transcodificação (Fase 2)

### Transcodificação H.264 Balanceada (MP4)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mov -progress pipe:1 -nostats -map 0 -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart output.mp4
```

### Transcodificação H.265 Compacta (MKV)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mp4 -progress pipe:1 -nostats -map 0 -c:v libx265 -preset slow -crf 28 -tag:v hvc1 -c:a aac -b:a 192k output.mkv
```

### Redimensionamento (Filtro Scale preservando proporção)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mp4 -progress pipe:1 -nostats -vf scale=1280:720:force_original_aspect_ratio=decrease -c:v libx264 -c:a aac output.mp4
```

### Stream Copy (Sem recodificação)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mkv -c:v copy -c:a copy output.mp4
```

---

## 4. Comandos das Ferramentas Especializadas (Fase 3)

### Compressão de Vídeo Otimizada
```bash
ffmpeg -nostdin -hide_banner -y -i input.mp4 -progress pipe:1 -nostats -map 0 -c:v libx264 -preset medium -crf 26 -pix_fmt yuv420p -vf scale=1280:-2 -c:a aac -b:a 128k output_compressed.mp4
```

### Extração de Áudio com Volume e Normalização EBU R128 (Loudnorm)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mp4 -progress pipe:1 -nostats -vn -c:a libmp3lame -b:a 192k -ar 44100 -ac 2 -af "volume=1.20,loudnorm" output_audio.mp3
```

### Corte Rápido de Vídeo (Stream Copy - Sem Perda)
```bash
ffmpeg -nostdin -hide_banner -y -ss 00:00:10.000 -to 00:00:25.000 -i input.mp4 -progress pipe:1 -nostats -map 0 -c copy output_trimmed.mp4
```

### Corte Preciso de Vídeo (Recodificação Quadro a Quadro)
```bash
ffmpeg -nostdin -hide_banner -y -ss 00:00:10.000 -to 00:00:25.000 -i input.mp4 -progress pipe:1 -nostats -map 0 -c:v libx264 -preset medium -crf 22 -pix_fmt yuv420p -c:a aac -b:a 192k output_accurate_trimmed.mp4
```

### Geração de GIF Animado com Paleta de Cores Otimizada (2 Passos)
```bash
ffmpeg -nostdin -hide_banner -y -ss 00:00:02.000 -t 00:00:05.000 -i input.mp4 -filter_complex "[0:v] fps=15,scale=480:-1:flags=lanczos,split [a][b];[a] palettegen [p];[b][p] paletteuse" output.gif
```

### Extração de Frame Estático (Thumbnail PNG)
```bash
ffmpeg -nostdin -hide_banner -y -ss 00:00:05.000 -i input.mp4 -frames:v 1 output_frame.png
```

---

## 5. Comandos Avançados (Fase 4)

### Concatenação Rápida (Concat Demuxer)
```bash
ffmpeg -nostdin -hide_banner -y -f concat -safe 0 -i .~tmp_concat_list.txt -progress pipe:1 -nostats -c copy output_merged.mp4
```

### Concatenação Compatível (Concat Filter com Normalização)
```bash
ffmpeg -nostdin -hide_banner -y -i clip1.mp4 -i clip2_noaudio.mp4 -filter_complex "[0:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30.000,format=yuv420p[v0];[0:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo[a0];[1:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30.000,format=yuv420p[v1];anullsrc=channel_layout=stereo:sample_rate=48000:d=2.000[a1];[v0][a0][v1][a1]concat=n=2:v=1:a=1[outv][outa]" -map "[outv]" -c:v libx264 -map "[outa]" -c:a aac output_compat.mp4
```

### Legendas (Soft-Embed e Burn-in)
```bash
ffmpeg -nostdin -hide_banner -y -i video.mp4 -i sub.srt -map 0 -map 1:0 -c:v copy -c:a copy -c:s copy -metadata:s:s:0 language=por -disposition:s:0 default out.mkv
ffmpeg -nostdin -hide_banner -y -i video.mp4 -vf "subtitles='sub.srt'" -c:v libx264 -c:a copy burned.mp4
```

### Motor de Filtros Composição Única (`-vf` / `-af`)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mp4 -map 0 -vf "crop=200:200:10:10,scale=160:160,hue=s=0,setpts=0.5000*PTS" -c:v libx264 -af "atempo=2.0000" -c:a aac filtered.mp4
```

### Remuxing e Edição/Limpeza de Metadados (Stream Copy)
```bash
ffmpeg -nostdin -hide_banner -y -i input.mkv -map 0:0 -map 0:2 -c copy -metadata:s:0 title="Main Video" -metadata:s:0 language=por remux.mkv
ffmpeg -nostdin -hide_banner -y -i input.mp4 -map 0 -c copy -map_metadata -1 cleared.mp4
```

### Teste Sintético e Codificação VA-API (Linux AMD/Intel)
```bash
ffmpeg -nostdin -hide_banner -y -vaapi_device /dev/dri/renderD128 -f lavfi -i testsrc=duration=0.3:size=256x256:rate=25 -vf format=nv12,hwupload -c:v h264_vaapi probe.mp4
```
