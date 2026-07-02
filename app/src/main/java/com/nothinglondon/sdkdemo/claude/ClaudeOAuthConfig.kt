package com.nothinglondon.sdkdemo.claude

object ClaudeOAuthConfig {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"
    const val TOKEN_URL = "https://claude.ai/v1/oauth/token"
    const val TOKEN_URL_CONSOLE = "https://console.anthropic.com/v1/oauth/token"
    /** Único redirect aceptado por el client_id de Claude Code. */
    const val REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"
    const val SCOPES =
        "org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload"
    const val USER_AGENT = "axios/1.13.6"
}