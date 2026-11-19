# Matriz de Funcionalidades — Morfia `1.0.0-rc.1` (`FEATURE_MATRIX.md`)

Classificação honesta das capacidades presentes na versão **`1.0.0-rc.1`**, separando funcionalidades estáveis (validadas por testes automatizados e execução no Fedora Linux), parciais e experimentais.

| Módulo / Funcionalidade | Estado | Observações Técnicas |
| :--- | :---: | :--- |
| **Inspeção de Mídia (FFprobe JSON)** | **Estável** | Extrai formato, duração, bitrate, vídeo, áudio, legendas, capítulos e outros streams |
| **Conversor Universal (MP4 / MKV / WebM)** | **Estável** | H.264 (`libx264`), H.265 (`libx265`), AAC, stream copy, CRF, presets, resolução e FPS |
| **Compressão Dedicada** | **Estável** | Perfis Leve/Equilibrado/Agressivo, slider CRF, comparativo antes/depois (sem promessa de tamanho fixo em CRF) |
| **Ferramentas de Áudio (Extração e `loudnorm`)** | **Estável** | MP3, AAC, FLAC, WAV, ganho, mixdown, sample rate, EBU R128 em 1 ou 2 passagens com fallback |
| **Corte de Vídeo (Rápido e Preciso)** | **Estável** | Stream copy (`-c copy`) e recodificação precisa |
| **GIF Animado em 2 Passagens e Frames** | **Estável** | `palettegen`/`paletteuse`, extração única, extração periódica com limite de segurança |
| **Conversão de Imagens** | **Estável** | PNG, JPEG, WEBP, BMP, TIFF, redimensionamento e preservação de alfa |
| **Concatenação Rápida e Compatível** | **Estável** | `concat` demuxer e `concat` filter com normalização e `anullsrc` |
| **Gerenciamento e Queima de Legendas** | **Estável** | Extração, soft-embed, burn-in (`subtitles`/`ass`), flags e deslocamento temporal |
| **Motor Tipado de Filtros e Preview** | **Estável** | Filtros geométricos, cor, tempo, áudio (`atempo` encadeado), proteção de RAM em `reverse` |
| **Streams e Metadados** | **Estável** | Seleção/reordenação por índice global e edição/limpeza seletiva de tags |
| **Biblioteca / Workspace Compartilhado** | **Estável** | Importação de arquivos/pastas, drag-and-drop, busca e envio para ferramentas |
| **Fila Persistente, Histórico e Recuperação** | **Estável** | Concorrência configurável, recuperação `INTERRUPTED`, duplicação e diagnóstico sanitizado |
| **Assistente de Configuração FFmpeg (9 etapas)** | **Estável** | Abre o aplicativo mesmo sem FFmpeg; desabilita apenas abas dependentes |
| **Temas (Retrô Escuro e Claro) e Idiomas (`pt_BR` / `en`)** | **Estável** | Alternância ao vivo com persistência em JSON |
| **Preview de Vídeo Proxy e Forma de Onda PCM** | **Parcial / Experimental** | Funcional via FFmpeg streaming + `SourceDataLine`; interação visual avançada de timeline permanece simples |
| **Aceleração por Hardware (`VAAPI`)** | **Experimental / Dependente de Hardware** | Sondagem real em 3 níveis sem fallback silencioso; no host Fedora com `mesa-va-drivers` pode reportar indisponibilidade (`hardwareFunctional=false`) |
| **Suporte Windows e macOS** | **Planejado (Não Validado Localmente)** | Camada `infrastructure/platform` e scripts prontos; validação nativa pendente |
