# Roadmap — Claude Glyph Limits

## Estado actual (v2.2.0)

**Modo de funcionamiento:** 100 % en el móvil. Sin PC, sin Syncthing, sin ficheros externos.

| Capa | Qué hace |
|---|---|
| OAuth on-device | Credenciales cifradas en el móvil (`EncryptedSharedPreferences`) |
| API en vivo | `GET api.anthropic.com/api/oauth/usage` al usar el toy |
| Auto-refresh | `POST claude.ai/v1/oauth/token` ~cada 8 h, solo en el móvil |
| Caché local | Última consulta OK si falla la red momentáneamente |

**Requisitos:** Phone (3), suscripción Claude.ai, internet al pulsar el Glyph Button, credenciales pegadas una vez.

## Hecho

- [x] Toy en matriz 25×25 (anillo + countdown)
- [x] OAuth + refresh automático en el móvil
- [x] Consulta on-demand (tap / long-press / AOD)
- [x] Almacenamiento cifrado de tokens
- [x] Publicado en GitHub + nothing.community
- [x] **v2.2.0:** eliminado fallback Syncthing / ficheros externos (vestigial en Android 15+)

## Próximo (corto plazo)

- [ ] **v2.2.x:** mensajes de error más claros en la app (token caducado vs sin red vs sin credenciales)
- [ ] **v2.2.x:** indicador en UI cuando muestra caché (`Fuente: cache`) vs API en vivo
- [ ] Release firmado (keystore) para instalación sin `adb` de debug

## Explorar (medio plazo)

- [ ] Flujo de login OAuth en la app (WebView / Custom Tab) — pegar JSON del PC solo la primera vez, o nunca
- [ ] Límite 7 días en long-press alternativo (doble tap o segundo gesto)
- [ ] Widget / notificación opcional con % 5h (sin depender de Glyph Button)
- [ ] Investigar si Anthropic permite sesiones OAuth independientes PC↔móvil (evitar invalidación cruzada al refrescar)

## Fuera de alcance / descartado

- ~~Syncthing / `claude-rate-limits.json`~~ — no fiable en Android 15 (permisos de almacenamiento); eliminado en v2.2.0
- ~~PC bridge / daemon~~ — el móvil debe ser autónomo
- ~~Polling en segundo plano~~ — consume batería y no aporta si el toy es on-demand

## Cómo contribuir

Issues y PRs en [github.com/literato1987/glyph-claude-limits](https://github.com/literato1987/glyph-claude-limits).