## Descrição
Descreva brevemente o objetivo deste Pull Request.

## Tipo de Alteração
- [ ] Bug fix (`fix`)
- [ ] Nova funcionalidade (`feat`)
- [ ] Documentação (`docs`)
- [ ] Empacotamento / CI (`build` / `ci`)
- [ ] Refatoração / Testes (`refactor` / `test`)

## Checklist de Arquitetura e Segurança
- [ ] O pacote `domain` não importa `javafx.*`, `java.awt.*` ou `infrastructure`.
- [ ] Nenhum comando externo é executado via shell (`/bin/sh -c`, `cmd.exe`); uso exclusivo de `ProcessBuilder` com `List<String>`.
- [ ] Nenhum emoji foi adicionado à interface gráfica (apenas `SVGPath`).
- [ ] `mvn clean verify` executado com 100% de aprovação.
- [ ] Documentação relevante em `docs/` atualizada.
