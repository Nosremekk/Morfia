# Guia de Desenvolvimento — FFmpeg Studio

Este documento orienta os desenvolvedores sobre a configuração do ambiente, execução local e fluxo de trabalho no Fedora Linux e outros sistemas compatíveis.

---

## 1. Ambiente Recomendado

- **Sistema Operacional**: Fedora Linux 40+ (testado no Fedora Linux 44 com kernel 7.2).
- **JDK**: OpenJDK 25 (ou superior).
- **Gerenciador de Build**: Apache Maven 3.9+.
- **Multimídia**: FFmpeg e FFprobe instalados (pacotes nativos do Fedora ou RPM Fusion).

### Instalação de Ferramentas no Fedora Linux
```bash
sudo dnf install java-25-openjdk java-25-openjdk-devel maven ffmpeg ffmpeg-free-devel
```

Caso queira suporte estendido a codecs proprietários adicionais:
```bash
sudo dnf install --allowerasing ffmpeg
```

---

## 2. Compilação e Testes

### Compilar o Código Fonte
```bash
mvn clean compile
```

### Rodar a Suíte de Testes
```bash
mvn test
```
*Dica*: Os testes de integração identificam automaticamente se o FFmpeg e FFprobe estão presentes no PATH da máquina e executam os testes com os binários reais.

### Executar a Aplicação com Interface Gráfica
```bash
mvn javafx:run
```

---

## 3. Configurações em Tempo de Execução

As preferências do usuário (caminhos dos binários, diretório padrão de saída, limite de concorrência) são salvas em:
- **Linux**: `~/.config/ffmpeg-studio/settings.json`
- **Logs**: `~/.config/ffmpeg-studio/logs/ffmpeg-studio.log`

O arquivo de configuração é criado automaticamente na primeira execução com valores padrão sãos.

---

## 4. Diretrizes de Contribuição

1. Todo código novo deve respeitar a arquitetura hexagonal e o padrão MVVM.
2. Não adicionar dependências sem discussão prévia e justificativa técnica.
3. Não criar funcionalidades visuais fictícias sem comportamento conectado ao backend/domínio.
4. Manter todos os testes automatizados passando antes de qualquer commit.
