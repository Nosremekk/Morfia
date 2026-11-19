# Sistema de Design e UI — FFmpeg Studio

Este documento consolida os padrões visuais, tokens de estilo, acessibilidade e componentes reutilizáveis do **FFmpeg Studio**.

---

## 1. Temas (`dark-theme.css` e `light-theme.css`)

- **Tema Escuro (`DARK`, padrão)**: Fundo `#0f1117`, superfície lateral `#141720`, cartões `#161923`, bordas `#252b3d`, destaque primário `#6366f1`, texto primário `#f8fafc`, texto secundário `#94a3b8`.
- **Tema Claro (`LIGHT`)**: Fundo `#f1f5f9`, superfície lateral `#ffffff`, cartões `#ffffff`, bordas `#cbd5e1`, destaque primário `#4f46e5`, texto primário `#0f172a`, texto secundário `#475569`.
- **Alternância em Tempo Real**: Aplicada via `FFmpegStudioApp.applyTheme(scene, theme)` ao salvar ou restaurar configurações.

---

## 2. Estados, Tipografia e Acessibilidade

- **Estados Desabilitados (`.button:disabled`, etc.)**: Opacidade `0.48`, cursor padrão.
- **Indicadores de Foco (`:focused`)**: Borda realçada `#818cf8` (1.5px) para navegação por teclado (`Tab` / `Shift+Tab`).
- **Badges Semânticos**: `.badge-success`, `.badge-warning`, `.badge-danger`, `.badge-info`, `.badge-neutral` sempre acompanhados de texto explícito (não dependem exclusivamente de cor).
- **Zero Emojis**: Todos os ícones de navegação e ações usam `SVGPath` vetorial via `IconHelper`.
- **Resoluções Alvo**: Layouts em `ScrollPane` (`fitToWidth="true"`) testados para adaptação entre `1366x768` e `1920x1080` (largura mínima `960x620`).

---

## 3. Componentes Reutilizáveis e Atalhos

- `UiComponents`: Diálogos de confirmação, alertas de erro com detalhes técnicos expansíveis, cópia para área de transferência e estados vazios (`.empty-state`).
- **Atalhos Globais**:
  - `Ctrl+O`: Importar arquivos na Biblioteca Compartilhada.
  - `Ctrl+Shift+O`: Importar pasta na Biblioteca Compartilhada.
  - `Ctrl+F`: Focar pesquisa na Biblioteca.
  - `Ctrl+,`: Abrir Configurações.
  - `Escape`: Retirar foco / retornar ao container principal.
