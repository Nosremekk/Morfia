# Aceleração por Hardware — FFmpeg Studio

O **FFmpeg Studio** diferencia explicitamente três níveis de disponibilidade de aceleração por hardware, nunca assumindo que a presença de um backend em `ffmpeg -hwaccels` garante funcionamento real.

---

## 1. Os Três Níveis de Verificação (`HardwareEncoderProbeResult`)

1. **Backend Compilado (`backendCompiled`)**: Presente na saída de `ffmpeg -hwaccels` (`vaapi`, `cuda`, `qsv`, `amf`, `videotoolbox`).
2. **Encoder Compilado (`encoderCompiled`)**: Presente na saída de `ffmpeg -encoders` (`h264_vaapi`, `hevc_vaapi`, `h264_nvenc`, etc.).
3. **Funcional no Hardware Local (`hardwareFunctional`)**: Comprovado por teste sintético real de codificação (`testsrc=duration=0.3:size=256x256:rate=25`) com timeout de 5 segundos, arquivo temporário isolado e limpeza automática.

> **Por que `256x256`?** Encoders de hardware como `hevc_vaapi` impõem largura mínima de hardware (ex.: `130-4096px`). Usar `256x256` evita falsos negativos por restrição dimensional.

---

## 2. Investigação no Ambiente Fedora Linux + AMD Radeon

Resultados observados no ambiente de desenvolvimento (Fedora Linux, AMD Ryzen 5 5600GT, Radeon iGPU):

- **Dispositivos DRM (`/dev/dri`)**: `card1` (`crw-rw----+`) e `renderD128` (`crw-rw-rw-`) presentes e acessíveis ao usuário.
- **VA-API (`h264_vaapi`, `hevc_vaapi`)**:
  - Utiliza `-vaapi_device /dev/dri/renderD128` e upload de frames via `-vf format=nv12,hwupload`.
  - **Evidência de Divergência (Flatpak SDK vs Host Fedora)**:
    - Ao executar `/usr/bin/ffmpeg` dentro do runtime Flatpak (com `codecs-extra` / Mesa completo), `h264_vaapi` e `hevc_vaapi` codificaram com sucesso (`27.2x` / `29.5x`).
    - Ao executar no **host Fedora real** (`flatpak-spawn --host ffmpeg`, mesmo contexto da JVM independente, Maven Surefire e aplicação JavaFX desktop), o pacote padrão `mesa-va-drivers` do Fedora não habilita perfis proprietários de codificação H.264/HEVC, retornando `[h264_vaapi] No usable encoding profile found.` (a menos que `mesa-va-drivers-freeworld` do RPM Fusion esteja instalado).
    - Portanto, o diagnóstico `hardwareFunctional=false` reportado pelo `HardwareAccelerationService` no host é **exato e fiel ao ambiente real da aplicação**.
- **AMF (`h264_amf`, `hevc_amf`)**:
  - Compilado no binário do FFmpeg, mas **não funcional** sem o runtime proprietário AMD (`DLL libamfrt64.so.1 failed to open`).
- **NVENC / QSV**:
  - Compilados no binário do FFmpeg do Fedora, mas indisponíveis neste hardware (`Cannot load libcuda.so.1` / `Error creating a MFX session: -9`).
- **VideoToolbox**:
  - Não compilado no Linux (exclusivo macOS).

---

## 3. Política de Fallback

- O software **nunca** realiza fallback silencioso de hardware para software alterando qualidade, codec ou comportamento esperado sem informar o usuário.
- Caso o usuário selecione um encoder de hardware indisponível ou que falhe na inicialização, o erro diagnóstico do driver é exibido e a seleção explícita de encoder por software (`libx264` / `libx265`) permanece disponível.
