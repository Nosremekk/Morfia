# Política de Segurança (`SECURITY.md`)

## 1. Escopo e Modelo de Ameaças

O **FFmpeg Studio** (nome provisório) é uma aplicação desktop **local-first e 100% offline**:
- Não possui servidores remotos, APIs externas, telemetria ou coleta de dados.
- Executa **FFmpeg** e **FFprobe** locais via `ProcessBuilder` com listas estruturadas de argumentos (`List<String>`), sem invocação de shell (`/bin/sh -c` / `cmd.exe`).
- A persistência local em JSON não utiliza desserialização polimórfica irrestrita (histórico e fila validam tipos contra whitelist fechada `JobHistoryEntry.ALLOWED_OPERATION_TYPES`).
- A descoberta automática de binários ignora diretórios temporários não confiáveis (`/tmp`, `/var/tmp`).

---

## 2. Versões Suportadas

| Versão | Suporte de Segurança |
| :--- | :--- |
| `1.0.0-rc.1` / `main` | Em preparação (Fase 6) |
| Fases anteriores (`0.1.0-SNAPSHOT`) | Não suportadas |

---

## 3. Como Reportar uma Vulnerabilidade

**Não abra issues públicas para vulnerabilidades de segurança.**

Quando o repositório público estiver hospedado no GitHub:
- Utilize o canal privado **GitHub Security Advisories** (*Security -> Advisories -> Report a vulnerability*) no repositório oficial do projeto.

Inclua no relato:
1. Descrição clara do problema e impacto potencial (ex.: injeção de argumentos, travessia de caminho, desserialização insegura, vazamento de dados em diagnóstico).
2. Passos exatos para reprodução e sistema operacional utilizado.
3. Versão da aplicação e do JDK/JavaFX.

---

## 4. Vulnerabilidades no FFmpeg / FFprobe

Vulnerabilidades nos binários externos do **FFmpeg** ou **FFprobe** devem ser reportadas diretamente ao projeto FFmpeg (https://ffmpeg.org/security.html) ou aos mantenedores da distribuição/empacotador do seu sistema operacional, mantendo os binários atualizados no ambiente local.
