# Diretrizes Visuais da Marca — Morfia (`BRAND_GUIDELINES.md`)

## 1. Conceito e Direção Artística (KD-006)
**Morfia — Multimedia Processing Studio** adota uma estética técnica **retrô**, inspirada em estações de trabalho multimídia e equipamentos profissionais de áudio e vídeo das décadas de 1990 e 2000 (como Winamp clássico, Cool Edit Pro, Sound Forge e estações SGI).
- **Foco funcional:** Geometria precisa, controles compactos, hierarquia clara, bordas estruturadas e alta legibilidade.
- **Restrições estéticas:** Sem glassmorphism, neumorphism, gradientes excessivos, cartões vazios, animações ornamentais ou efeitos CRT/scanlines sobre textos e controles.

---

## 2. Logotipo Original
- **Arquivo-fonte vetorial:** [`docs/branding/logo/morfia-logo.svg`](logo/morfia-logo.svg) (e cópia embutida em `src/main/resources/com/ffmpegstudio/icons/morfia-logo.svg`).
- **Variantes monocromáticas:** [`morfia-logo-mono-light.svg`](logo/morfia-logo-mono-light.svg) e [`morfia-logo-mono-dark.svg`](logo/morfia-logo-mono-dark.svg).
- **Conceito:** Um **"M" geométrico modular** emoldurado por placa técnica de workstation, acompanhado por barras de pulso em âmbar e bege representando fluxo e transformação de mídia.
- **Recursos raster gerados via `/usr/bin/rsvg-convert`:**
  - `packaging/icons/morfia-{16,32,48,64,128,256,512,1024}.png`
  - `packaging/icons/morfia.ico` (empacotado com os PNGs 16–256)
  - *Nota:* `.icns` para macOS é gerado no runner macOS ou ferramenta compatível a partir dos PNGs existentes (não gerado localmente no Fedora).

---

## 3. Paleta Oficial (Tema Escuro Retrô)

| Token / Papel | Cor Hex | Aplicação |
| :--- | :---: | :--- |
| **Fundo Principal** | `#171B18` | Área principal de conteúdo e fundo raiz |
| **Superfícies / Cards** | `#292E29` / `#1F2420` | Painéis, barra lateral e cartões |
| **Bordas Estruturais** | `#454D40` | Divisores, contornos de cards e inputs |
| **Destaque Principal** | `#B7C99B` | Botões primários (com texto `#171B18`), aba ativa e foco |
| **Texto Primário** | `#EAEFE5` | Títulos e texto principal |
| **Texto Secundário** | `#D5C7A5` | Subtítulos, rótulos e metadados |
| **Destaque Auxiliar** | `#B88E60` | Badges auxiliares e acentos de progresso |

O **Tema Claro (`light-theme.css`)** permanece preservado para acessibilidade e preferência do usuário.

---

## 4. Tipografia e Iconografia
- **Texto comum:** Fontes nativas legíveis do sistema (`Inter`, `Segoe UI`, `Cantarell`, `Ubuntu`, sans-serif).
- **Dados técnicos (`.mono-field`):** Fonte monoespaçada para timestamps, duração, bitrate, FPS, resolução, logs e relatórios de diagnóstico.
- **Iconografia:** Ícones vetoriais SVG desenhados via `SVGPath` em `IconHelper.java` (zero emojis na interface).

---

## 5. Origem e Licenciamento dos Recursos
Todos os arquivos SVG, PNG, ICO e folhas de estilo CSS do Morfia são criações originais do projeto e distribuídos sob a licença **MIT** (`Copyright (c) 2026 Emerson Vinicius Santos de Santana`).
