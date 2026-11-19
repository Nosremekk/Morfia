# Auditoria Técnica — Fases 1, 2 e 3 (Pré-Fase 4)

**Data da Auditoria**: 2026-11-18  
**Escopo**: Validação estrutural, execução de testes via `mvn clean verify`, inspeção dos relatórios XML do Maven Surefire, verificação de repositório Git e auditoria funcional dos requisitos das Fases 1 a 3.

---

## 1. Contagem Real de Testes (Relatórios XML Surefire)

Execução de `mvn clean verify` finalizada com `BUILD SUCCESS` (`exit code 0`). Não há relatórios Failsafe (`*IT.xml`), pois todos os testes unitários e de integração residem sob o ciclo do `maven-surefire-plugin`.

| Classe de Teste | Testes | Falhas | Erros | Ignorados |
| :--- | :---: | :---: | :---: | :---: |
| `JobQueueServiceTest` | 3 | 0 | 0 | 0 |
| `AudioToolsOperationTest` | 2 | 0 | 0 | 0 |
| `CompressOperationTest` | 3 | 0 | 0 | 0 |
| `ConversionValidatorTest` | 3 | 0 | 0 | 0 |
| `GenerateGifAndFramesOperationTest` | 2 | 0 | 0 | 0 |
| `ProcessingJobTest` | 4 | 0 | 0 | 0 |
| `TrimOperationTest` | 3 | 0 | 0 | 0 |
| `JsonPresetRepositoryTest` | 3 | 0 | 0 | 0 |
| `JsonSettingsRepositoryTest` | 2 | 0 | 0 | 0 |
| `CapabilityIntegrationTest` | 2 | 0 | 0 | 0 |
| `FFmpegCapabilityDetectorTest` | 4 | 0 | 0 | 0 |
| `FFmpegCommandBuilderTest` | 4 | 0 | 0 | 0 |
| `FFmpegProcessExecutorTest` | 4 | 0 | 0 | 0 |
| `FFmpegProgressParserTest` | 4 | 0 | 0 | 0 |
| `FFprobeClientTest` | 2 | 0 | 0 | 0 |
| `MultimediaToolsIntegrationTest` | 5 | 0 | 0 | 0 |
| `TranscodeIntegrationTest` | 2 | 0 | 0 | 0 |
| `FileSystemServiceTest` | 3 | 0 | 0 | 0 |
| **TOTAL (Pré-correções de auditoria)** | **55** | **0** | **0** | **0** |

### Investigação da Divergência no Relatório Anterior
O relatório de fechamento da Fase 3 declarou corretamente o total de **55 testes**, porém listou incorretamente `FFprobeClientIntegrationTest (22 tests)` e omitiu 4 classes de testes (`FFmpegCapabilityDetectorTest`, `FFmpegCommandBuilderTest`, `FFmpegProcessExecutorTest` e `FFmpegProgressParserTest`), além de atribuir 22 testes a uma classe inexistente (`FFprobeClientTest` possui 2 testes). A soma real nos arquivos XML em `target/surefire-reports/` é exatamente **55 testes**.

*(Nota: após a inclusão do teste de colisão de destino em `JobQueueServiceTest` nesta auditoria, a suíte passa a contar com 56 testes).*

---

## 2. Investigação do Código 128 (`git status`)

O comando `git status` retornou `exit code 128` (`fatal: not a git repository (or any parent up to mount point /)`).  
**Diagnóstico**: O diretório `/home/vini/ffmpeg-visual` não foi inicializado como repositório Git (ausência do diretório `.git`). Conforme as diretrizes, nenhum repositório foi inicializado automaticamente.

---

## 3. Auditoria Funcional e Técnica

### 3.1. Normalização `loudnorm`
- **Estado encontrado**: `AudioToolsOperation` utiliza `loudnorm=I=-16:TP=-1.5:LRA=11` em **passagem única** (normalização dinâmica).
- **Avaliação**: Adequado para processamento rápido em passagem única, porém não executa análise prévia (dupla passagem) para normalização linear exata. Documentado como limitação conhecida da Fase 3; na Fase 4.3 (Motor de Filtros), a distinção permanece explícita.

### 3.2. Extração Periódica de Frames e Conversão de Imagens
- **Estado encontrado**: `ExtractFramesOperation` suporta apenas captura de **frame único** (`-frames:v 1`) em um timestamp específico (PNG ou JPEG).
- **Lacuna identificada**: Extração periódica em intervalos (`fps=1/N`) e conversão isolada entre formatos de imagem não foram implementadas na Fase 3. Registrado como pendência documental para não haver declaração falsa de cobertura.

### 3.3. Pré-visualização de Pontos de Corte
- **Estado encontrado**: A aba *Corte de Vídeo (Trim)* calcula a duração resultante e valida intervalos, mas não exibe prévia visual dos frames nos pontos inicial e final. Será endereçada pelo serviço assíncrono de pré-visualização da **Fase 4.4**.

### 3.4. Segurança de Arquivos Temporários e Colisões de Destinos
- **Problema encontrado**: `ProcessingJob.create(...)` gerava caminhos temporários no formato `.~tmp_<nome_do_arquivo>`. Em execuções concorrentes na fila, duas tarefas com o mesmo nome de destino podiam colidir no mesmo arquivo temporário ou sobrescrever o mesmo destino final.
- **Correção aplicada**:
  1. `ProcessingJob.create(...)` agora inclui um identificador único de 8 caracteres (`.~tmp_<uuid8>_<nome>`).
  2. `JobQueueService.addJob(...)` e `addJobs(...)` agora validam colisões de caminho de saída normalizado entre tarefas `PENDING` e `RUNNING`, rejeitando colisões com `IllegalArgumentException`.

### 3.5. Cancelamento e Encerramento de Processos
- **Estado encontrado**: `FFmpegProcessExecutor.LiveProcessHandle.cancel()` encerra a árvore de processos descendentes (`process.descendants()`), tenta encerramento gracioso (`destroy()` com espera de 1s) seguido de `destroyForcibly()`, e remove o arquivo temporário. O fluxo está sólido e coberto por `TranscodeIntegrationTest`.

### 3.6. Composição de Filtros
- **Estado encontrado**: `FFmpegCommandBuilder` compõe uma única cadeia `-vf` separada por vírgulas (`scale`, `fps`), e `GenerateGifOperation` utiliza `-filter_complex` com `split`, `palettegen` e `paletteuse`. Não há duplicação de `-vf`. O motor genérico da Fase 4.3 expandirá essa capacidade de forma tipada.

### 3.7. Compatibilidade Codecs/Contêineres e Presets
- **Estado encontrado**: `ConversionValidator` valida compatibilidade de `ContainerFormat` (`MP4`, `MKV`) e encoders reais em `FFmpegCapabilities`. `JsonPresetRepository` protege presets de fábrica (`isBuiltIn = true`) contra exclusão e suporta importação/exportação JSON.

### 3.8. Afirmações Enganosas sobre Redução Percentual com CRF
- **Problema encontrado**: `CompressOperation.CompressionProfile` e `compression.fxml` exibiam estimativas percentuais fixas (`~20-30% reduction`, `~50% reduction`, `~25% menor`, `~50% menor`, `~70% menor`) associadas a perfis baseados em CRF.
- **Correção aplicada**: As estimativas percentuais foram removidas dos enums e da interface `compression.fxml`, passando a indicar claramente o valor de CRF de referência (`CRF 22`, `CRF 26`, `CRF 30`), mantendo o callout técnico de que o tamanho final depende da complexidade das cenas.
