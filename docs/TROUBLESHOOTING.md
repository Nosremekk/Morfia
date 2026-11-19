# Guia de Resolução de Problemas (Troubleshooting) — FFmpeg Studio

## 1. VA-API retorna `No usable encoding profile found` no Fedora Linux
- **Causa**: O pacote padrão `mesa-va-drivers` no Fedora não inclui perfis proprietários de codificação H.264/HEVC via VA-API.
- **Diagnóstico**: Acesse **Configurações → Hardware Acceleration → Testar Encoders em Hardware Real**. O aplicativo exibe o erro exato do driver sem realizar fallback silencioso.
- **Solução**: Utilize os encoders por software (`libx264`, `libx265`) ou instale os drivers freeworld do RPM Fusion caso deseje aceleração VA-API no host.

## 2. `amf` / `nvenc` / `qsv` aparecem como indisponíveis
- **Causa**: O binário do FFmpeg foi compilado com suporte a múltiplos backends, porém as bibliotecas/hardware locais não estão presentes (`libamfrt64.so.1`, `libcuda.so.1`, MFX session).

## 3. Queima de legendas (Burn-in) falha
- **Causa**: O filtro `subtitles` / `ass` exige compilação do FFmpeg com `libass`, ou a legenda selecionada é gráfica (`PGS` / `DVD_SUBTITLE`), que não é suportada pelo filtro textual.

## 4. Limite de memória no filtro `reverse`
- **Causa**: `FilterGraphBuilder` bloqueia clipes cuja estimativa conservadora de quadros descomprimidos em RAM exceda 4 GB (ou duração > 120s). Recorte o trecho antes de aplicar `reverse`.

## 5. Tarefas interrompidas após fechamento inesperado
- Ao reabrir a aplicação, tarefas que estavam `RUNNING` aparecem como `INTERRUPTED` e solicitam confirmação antes de serem recolocadas como `PENDING`.
