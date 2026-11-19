# Third-Party Notices & Dependency Inventory

Este documento lista as dependências de terceiros utilizadas pelo **Morfia**, suas licenças, finalidades e obrigações de redistribuição.

> **Nota sobre o código autoral:** O código-fonte autoral deste projeto é distribuído sob a licença **MIT** ([LICENSE](LICENSE)). A adoção da licença MIT para o código autoral não altera as licenças das dependências listadas abaixo.

---

## 1. Dependências de Runtime e Interface (Redistribuíveis em Pacotes Desktop)

| Componente | Versão | Licença | Origem | Utilização | Obrigações de Redistribuição |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OpenJDK / Java Runtime** | 25 | GPL-2.0 with Classpath Exception | https://openjdk.org/ | Runtime JVM e módulos base empacotados via `jlink`/`jpackage` | Incluir texto GPL-2.0 + Classpath Exception nos instaladores que embutem runtime |
| **JavaFX (`javafx-base`, `javafx-controls`, `javafx-fxml`, `javafx-graphics`)** | 24 | GPL-2.0 with Classpath Exception | https://openjfx.io/ | Interface gráfica desktop, FXML e CSS | Incluir aviso de copyright OpenJFX e GPL-2.0 + Classpath Exception |
| **Jackson (`jackson-databind`, `jackson-datatype-jsr310`, `jackson-core`, `jackson-annotations`)** | 2.18.2 | Apache-2.0 | https://github.com/FasterXML/jackson | Serialização/desserialização JSON (configurações, presets, fila/histórico, FFprobe) | Preservar aviso de copyright e texto da licença Apache-2.0 (e arquivo NOTICE quando aplicável) |
| **SLF4J (`slf4j-api`)** | 2.0.16 | MIT | https://www.slf4j.org/ | Facade de logging | Preservar aviso de copyright e licença MIT |
| **Logback (`logback-classic`, `logback-core`)** | 1.5.16 | EPL-1.0 / LGPL-2.1 (dual) | https://logback.qos.ch/ | Implementação local de logs em arquivo/console | Incluir aviso EPL-1.0 / LGPL-2.1 |

---

## 2. Dependências de Teste (Apenas Escopo `test` — Não Redistribuídas)

| Componente | Versão | Licença | Origem | Utilização |
| :--- | :--- | :--- | :--- | :--- |
| **JUnit Jupiter (`junit-jupiter-api`, `engine`, `params`)** | 5.11.4 | EPL-2.0 | https://junit.org/junit5/ | Framework de testes automatizados |
| **Mockito (`mockito-core`, `mockito-junit-jupiter`)** | 5.14.2 | MIT | https://site.mockito.org/ | Test doubles em testes unitários |
| **AssertJ (`assertj-core`)** | 3.27.3 | Apache-2.0 | https://assertj.github.io/doc/ | Asserções fluentes em testes |

---

## 3. Recursos Gráficos e Tipografia

- **Ícones da Interface (`IconHelper.java`):** Ícones vetoriais SVG (`SVGPath`) escritos diretamente no código-fonte autoral sob licença **MIT**, sem dependência de pacotes de ícones externos.
- **Fontes:** Fontes padrão do sistema operacional e família `Monospace` / `System`, sem redistribuição de arquivos `.ttf`/`.otf` de terceiros.

---

## 4. Executáveis Externos Configurados pelo Usuário (Não Redistribuídos)

- **FFmpeg e FFprobe** (https://ffmpeg.org/)
- **Relação técnica e jurídica:** Nesta versão (`1.0.0-rc.1`), **FFmpeg e FFprobe não são incorporados ao código Java nem redistribuídos nos instaladores**. O aplicativo invoca executáveis externos já presentes ou instalados pelo usuário no sistema operacional por meio de `ProcessBuilder` com listas estruturadas de argumentos.
- **Licenças dos executáveis externos:** Dependem da compilação instalada pelo usuário (tipicamente **LGPL-2.1+** ou **GPL-2.0+** / **GPL-3.0+** conforme codecs habilitados como `libx264`/`libx265`).
- Esta separação reflete a arquitetura atual de invocação de processos externos e **não constitui aconselhamento jurídico nem garantia absoluta para integrações nativas futuras (JNI/FFM)**.
