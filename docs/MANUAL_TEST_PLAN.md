# Plano de Validação Manual — FFmpeg Studio

## 1. Inicialização, Tema e Idioma
1. Executar `mvn javafx:run`.
2. Verificar abertura da janela em `1200x740`, redimensionar para `1366x768` e `1920x1080` garantindo ausência de cortes ou sobreposições.
3. Em **Configurações**, alternar idioma (`pt_BR` ↔ `en`) e tema (`DARK` ↔ `LIGHT`), clicar em **Save** e confirmar atualização imediata.

## 2. Área de Trabalho Compartilhada & Atalhos
1. Pressionar `Ctrl+O`, importar múltiplos vídeos (incluindo caminhos com espaços e caracteres Unicode).
2. Usar `Ctrl+F` para filtrar por nome, alterar filtro de tipo e ordenação.
3. Encaminhar um vídeo da Biblioteca para **Converter**, **Compressão** e **Filtros**.
4. Remover a referência da Biblioteca e confirmar que o arquivo original no disco permanece intacto.

## 3. Ferramentas e Pré-visualização
1. Em **Filtros**, adicionar `Resize` + `Escala de Cinza`, mover o slider de timestamp e confirmar a atualização Antes/Depois com *debounce*.
2. Em **Concatenação**, testar 2 clipes incompatíveis no modo *Rápido* (deve bloquear) e no modo *Compatível* (deve normalizar).
3. Em **Legendas**, testar soft-embed `.srt` em `.mkv` e verificar faixa no inspetor.
4. Em **Streams & Metadados**, remover uma faixa de áudio via stream copy e editar título/artista.

## 4. Fila Persistente, Histórico e Encerramento Seguro
1. Enfileirar 2 tarefas longas, iniciar a fila e fechar a janela durante execução: confirmar exibição do diálogo de encerramento seguro.
2. Reabrir a aplicação com tarefa pendente/interrompida e verificar detecção e confirmação de reenvio.
3. Acessar **Histórico**, duplicar uma tarefa concluída e exportar um relatório na **Central de Diagnóstico** (sem caminhos privados por padrão).
