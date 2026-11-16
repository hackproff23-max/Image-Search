# Wallpapers 4K

App Android (Kotlin + Jetpack Compose) que busca wallpapers em alta resolução e salva em `Pictures/Wallpapers4K`.

- **Orientação:** Comprido (vertical) ou Horizontal
- **Resolução mínima:** múltiplo (2x, 3x, 4x por lado) da tela do Redmi 13C (720×1600). Padrão 2x = 1440×3200 (4x a área em pixels)
- **Fontes:** Wallhaven (sem chave, SFW) e Pexels (opcional, requer chave gratuita)

## Build

O GitHub Actions compila a cada push na `main` e publica o APK em *Actions → Build APK → Artifacts*.

Para ativar o Pexels, crie o secret `PEXELS_KEY` em *Settings → Secrets and variables → Actions*.

Local: `gradle assembleDebug -PPEXELS_KEY=sua_chave`
