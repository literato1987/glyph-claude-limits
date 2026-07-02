# Share draft — nothing.community

**Title:** Claude Glyph Limits — Claude.ai usage ring on Phone (3) matrix

**Body:**

I built a Glyph Toy for the Nothing Phone (3) that shows your **Claude.ai 5-hour usage limit** on the back matrix:

- **Tap** Glyph Button: Claude icon + ring filling with % used
- **Long-press:** countdown until reset (`4:29` format)
- Fetches **directly from Anthropic's OAuth usage API** on the phone
- Auto-refreshes OAuth tokens — **fully standalone**, no PC / no Syncthing

Setup: paste `jq '.claudeAiOauth' ~/.claude/.credentials.json` into the app once. After that the PC can stay off.

**GitHub:** https://github.com/literato1987/glyph-claude-limits  
**APK (v2.2.0):** https://github.com/literato1987/glyph-claude-limits/releases/download/v2.2.0/glyph-claude-limits-v2.2.0.apk

**Requires:** Phone (3), Claude Code / Claude.ai OAuth credentials, internet when using the toy.

Built with the official [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit). Not affiliated with Anthropic.

**Status:** pendiente — el foro no permite dos replies seguidas del mismo usuario. Publicar como reply nuevo cuando alguien responda en el hilo.

Reply in: https://nothing.community/d/40994-community-glyph-matrix-toys-collection