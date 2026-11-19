# Compatibilidade de Formatos, Codecs e Legendas — FFmpeg Studio

Este documento registra as regras de validação de compatibilidade implementadas no domínio (`ConversionValidator`, `ConcatOperation`, `SubtitleOperation`, `StreamOperation`).

---

## 1. Contêineres de Vídeo (`ContainerFormat`)

| Contêiner | Codecs de Vídeo | Codecs de Áudio | Legendas Suportadas | Anexos (`attachment`) |
| :--- | :--- | :--- | :--- | :--- |
| **MP4** (`.mp4`) | H.264 (`libx264`, `h264_vaapi`), H.265 (`libx265`, `hevc_vaapi`) | AAC (`aac`), Stream Copy | `mov_text` (SRT/WebVTT convertidos) | Não suportado |
| **MKV** (`.mkv`) | H.264, H.265, VP9 (`libvpx-vp9`), Stream Copy | AAC, MP3, FLAC, WAV/PCM, Stream Copy | SRT, ASS, SSA, WebVTT, PGS, DVD Subtitle | Suportado |

---

## 2. Regras de Legendas (`SubtitleFormat` & `SubtitleOperation`)

- **Legendas Textuais**: `SRT`, `ASS`, `SSA`, `WEBVTT`, `MOV_TEXT`.
- **Legendas Gráficas (Bitmap)**: `PGS` (`hdmv_pgs_subtitle`), `DVD_SUBTITLE` (`dvd_subtitle`).
- **Proibição de OCR Implícito**: O sistema bloqueia conversões diretas entre legendas gráficas (`PGS`, `DVD_SUBTITLE`) e textuais (`SRT`, `ASS`, `WEBVTT`) durante extração ou queima textual.
- **MP4 vs MKV**:
  - `MP4` rejeita incorporação de `PGS` e `DVD_SUBTITLE`.
  - `ASS`/`SSA` em `MP4` via soft-embed são bloqueados com orientação para usar `MKV` ou queima (*Burn-in*).
- **Queima de Legendas (*Burn-in*)**: Requer os filtros `subtitles` ou `ass` (`libass`) detectados em `FFmpegCapabilities` e realiza recodificação de vídeo com cópia do áudio.

---

## 3. Concatenação (`ConcatOperation`)

| Modalidade | Requisitos | Comportamento em Incompatibilidade |
| :--- | :--- | :--- |
| **Rápida (`FAST_DEMUXER`)** | Mesmos codecs de vídeo e áudio, mesma resolução, mesmo pixel format, mesmo sample rate/canais e mesma presença de áudio/vídeo. | **Bloqueada na validação** antes da execução, orientando o uso do modo Compatível. |
| **Compatível (`COMPATIBLE_REENCODE`)** | Qualquer combinação de resoluções, codecs, FPS ou clipes sem áudio. | Normaliza resolução com `scale` + `pad`, `setsar=1`, `fps`, `yuv420p`, áudio `48000 Hz stereo` e gera silêncio (`anullsrc`) para clipes sem áudio. |
