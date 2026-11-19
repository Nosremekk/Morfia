# Persistência de Configurações, Fila e Histórico — FFmpeg Studio

---

## 1. Decisão Arquitetural: JSON Versionado vs SQLite

Optou-se por **JSON versionado com escrita atômica** (`Jackson` já presente no projeto) em vez de introduzir `sqlite-jdbc`, pelos seguintes motivos:
1. Mantém zero dependências nativas adicionais para empacotamento desktop (`jpackage`).
2. O volume local esperado de fila e histórico (centenas de registros, teto de 500 itens no histórico com poda por retenção de dias) é atendido com latência sub-milissegundo em memória + persistência atômica.
3. Escrita segura via arquivo temporário `.~tmp_*` e `Files.move(..., REPLACE_EXISTING)`.

---

## 2. Arquivos Persistidos (`~/.config/ffmpeg-studio/`)

| Arquivo | Schema | Conteúdo |
| :--- | :---: | :--- |
| `settings.json` | v2 | Caminhos `ffmpeg`/`ffprobe`, saída, `tempDir`, tema, idioma (`pt_BR`/`en`), concorrência, limite de cache, retenção de logs, confirmações. Migração automática de arquivos v1 (5 campos). |
| `presets.json` | v1 | Presets personalizados do usuário (presets de fábrica são imutáveis em código). |
| `queue-state.json` | v1 | Tarefas `PENDING`, `RUNNING` ou `INTERRUPTED` da fila ativa. |
| `history.json` | v1 | Histórico de tarefas `COMPLETED`, `FAILED` e `CANCELLED` (máx. 500 registros + limpeza por `logRetentionDays`). |

---

## 3. Segurança e Recuperação de Tarefas Interrompidas

- **Sem Desserialização Polimórfica Irrestrita**: `JobHistoryEntry` valida `operationType` contra uma *allowlist* fechada (`ALLOWED_OPERATION_TYPES`) e reconstrói apenas DTOs tipados conhecidos.
- **Recuperação Segura**: Ao reiniciar a aplicação, qualquer tarefa salva no estado `RUNNING` é convertida para **`JobStatus.INTERRUPTED`**. O sistema **nunca** reinicia tarefas interrompidas automaticamente e **não** promete retomada a partir do frame exato; solicita confirmação explícita ao usuário para recolocá-las como `PENDING` ou descartá-las.
- **Exclusão Segura**: Remover registros do histórico nunca exclui arquivos multimídia no disco.
