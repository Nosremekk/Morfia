# Licenciamento (`LICENSING.md`)

## 1. Código Autoral — Licença MIT

Conforme decisão **KD-002**, o código-fonte autoral deste projeto é licenciado sob a **Licença MIT (`SPDX: MIT`)**, cujo texto integral encontra-se em [LICENSE](../LICENSE).

- **Ano:** 2026
- **Titular dos Direitos Autorais:** Emerson Vinicius Santos de Santana
- **Metadados:** Declarado em `pom.xml` (`<licenses>`), `AppIdentity.LICENSE_SPDX` e na seção Sobre do Assistente (`setup-wizard.fxml`).

---

## 2. Dependências de Terceiros

A adoção da licença MIT no código autoral não altera as licenças das bibliotecas de terceiros utilizadas em compilação, runtime ou empacotamento. Consulte o inventário completo em [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md):

- **OpenJDK 25 / JavaFX 24:** GPL-2.0 with Classpath Exception
- **Jackson 2.18.11:** Apache-2.0
- **SLF4J 2.0.16:** MIT
- **Logback 1.5.34:** EPL-1.0 / LGPL-2.1
- **JUnit 5 / Mockito / AssertJ (escopo `test`):** EPL-2.0, MIT, Apache-2.0

Todo pacote distribuível gerado via `jpackage` inclui obrigatoriamente os arquivos `LICENSE` e `THIRD_PARTY_NOTICES.md`.

---

## 3. Separação de Executáveis Externos (FFmpeg e FFprobe)

- Na versão `1.0.0-rc.1`, **FFmpeg e FFprobe não são vinculados estaticamente ou dinamicamente ao processo Java** (sem JNI/FFM) e **não são redistribuídos nos instaladores**.
- O usuário instala ou aponta os executáveis externos em seu próprio sistema operacional, e o aplicativo os invoca como processos independentes via `ProcessBuilder` (`List<String>`).
- Os binários do FFmpeg/FFprobe permanecem regidos por suas respectivas licenças (tipicamente LGPL-2.1+ ou GPL-2.0+/3.0+ conforme os codecs compilados).
- **Aviso:** Esta descrição refere-se à arquitetura atual de invocação de processos externos e não constitui garantia jurídica absoluta para eventuais integrações nativas futuras.
