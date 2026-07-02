package com.nothinglondon.sdkdemo

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nothinglondon.sdkdemo.claude.ClaudeAuthManager
import com.nothinglondon.sdkdemo.claude.ClaudeLimits
import com.nothinglondon.sdkdemo.claude.ClaudeLimitsProvider
import com.nothinglondon.sdkdemo.claude.ClaudeOAuthClient
import com.nothinglondon.sdkdemo.claude.ClaudeUsageException
import com.nothinglondon.sdkdemo.claude.OAuthCredentials
import com.nothinglondon.sdkdemo.claude.OAuthSessionStore
import com.nothinglondon.sdkdemo.claude.TokenStore
import com.nothinglondon.sdkdemo.claude.formatResetCountdown
import com.nothinglondon.sdkdemo.ui.theme.NothingAndroidSDKDemoTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NothingAndroidSDKDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ClaudeLimitsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onActivateToy = ::openGlyphToyManager,
                        onStartOAuthLogin = ::startOAuthLogin,
                        refreshLimits = { ClaudeLimitsProvider.refresh(this) },
                        hasCredentials = { TokenStore.hasCredentials(this) },
                        loadCredentials = { TokenStore.loadCredentials(this) },
                        saveCredentials = { TokenStore.saveCredentials(this, it) },
                        clearCredentials = ::clearCredentials,
                        completeAuthorization = { code, state ->
                            ClaudeAuthManager.completeAuthorization(this, code, state)
                        },
                        hasPendingOAuth = { OAuthSessionStore.load(this) != null },
                    )
                }
            }
        }
    }

    private fun startOAuthLogin() {
        OAuthSessionStore.clear(this)
        val session = ClaudeOAuthClient.startSession()
        OAuthSessionStore.save(this, session)
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(ClaudeOAuthClient.buildAuthorizationUrl(session)))
    }

    private fun clearCredentials() {
        TokenStore.clearCredentials(this)
        OAuthSessionStore.clear(this)
    }

    private fun openGlyphToyManager() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
            )
        }
        startActivity(intent)
    }
}

@Composable
fun ClaudeLimitsScreen(
    modifier: Modifier = Modifier,
    onActivateToy: () -> Unit,
    onStartOAuthLogin: () -> Unit,
    refreshLimits: suspend () -> ClaudeLimits,
    hasCredentials: () -> Boolean,
    loadCredentials: () -> OAuthCredentials?,
    saveCredentials: (OAuthCredentials) -> Unit,
    clearCredentials: () -> Unit,
    completeAuthorization: suspend (String, String?) -> OAuthCredentials,
    hasPendingOAuth: () -> Boolean,
) {
    var limits by remember { mutableStateOf(ClaudeLimits.EMPTY) }
    var isLoading by remember { mutableStateOf(true) }
    var credentialsInput by remember { mutableStateOf("") }
    var authCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var configured by remember { mutableStateOf(hasCredentials()) }
    var expiresLabel by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var awaitingCode by remember { mutableStateOf(hasPendingOAuth() || !hasCredentials()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val scrollState = rememberScrollState()

    fun reload() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                limits = refreshLimits()
                loadCredentials()?.let { creds ->
                    expiresLabel = timeFormat.format(Date(creds.expiresAtMs))
                }
                configured = hasCredentials()
                if (limits.usedPercentage < 0) {
                    errorMessage = when {
                        !configured -> "Inicia sesión con Claude para ver los límites"
                        limits.sourceLabel == "cache" -> "Sin conexión — mostrando última lectura"
                        else -> "No se pudieron obtener los límites"
                    }
                }
            } catch (e: ClaudeUsageException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error de red"
            } finally {
                isLoading = false
            }
        }
    }

    fun finishAuthorization(code: String, state: String?) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                completeAuthorization(code, state)
                configured = hasCredentials()
                awaitingCode = false
                authCodeInput = ""
                reload()
            } catch (e: ClaudeUsageException) {
                errorMessage = e.message
                isLoading = false
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error al completar el login"
                isLoading = false
            }
        }
    }

    fun pasteFromClipboard() {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clip.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            authCodeInput = text
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Claude Glyph Limits",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (!configured) {
            Text(
                text = stringResource(R.string.login_instructions),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    awaitingCode = true
                    onStartOAuthLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.login_with_claude))
            }
        } else {
            Text(
                text = stringResource(R.string.logged_in_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    awaitingCode = true
                    onStartOAuthLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.relogin_with_claude))
            }
        }

        AnimatedVisibility(awaitingCode || authCodeInput.isNotBlank() || hasPendingOAuth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auth_code_instructions),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = authCodeInput,
                    onValueChange = { authCodeInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.auth_code_label)) },
                    minLines = 1,
                    maxLines = 3,
                )
                OutlinedButton(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.paste_auth_code))
                }
                Button(
                    onClick = {
                        val (code, state) = ClaudeOAuthClient.parseAuthorizationResponse(authCodeInput)
                        if (code.isBlank()) {
                            errorMessage = "Código vacío"
                            return@Button
                        }
                        finishAuthorization(code, state)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = authCodeInput.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.submit_auth_code))
                }
            }
        }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(text = if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas (pegar JSON)")
        }

        AnimatedVisibility(showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.token_instructions),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = credentialsInput,
                    onValueChange = { credentialsInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.oauth_credentials_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default,
                    minLines = 3,
                    maxLines = 6,
                )
                Button(
                    onClick = {
                        val parsed = OAuthCredentials.parseJson(credentialsInput)
                        if (parsed == null) {
                            errorMessage = "JSON inválido — pega el bloque claudeAiOauth completo"
                            return@Button
                        }
                        saveCredentials(parsed)
                        configured = hasCredentials()
                        awaitingCode = false
                        reload()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = credentialsInput.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.save_token))
                }
            }
        }

        if (configured) {
            TextButton(onClick = {
                clearCredentials()
                credentialsInput = ""
                authCodeInput = ""
                configured = false
                limits = ClaudeLimits.EMPTY
                expiresLabel = null
                awaitingCode = true
            }) {
                Text(text = stringResource(R.string.clear_token))
            }
        }

        when {
            isLoading -> {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.loading_limits))
            }

            limits.usedPercentage >= 0 -> {
                Text(
                    text = stringResource(
                        R.string.limits_status,
                        limits.usedPercentage,
                        formatResetCountdown(limits.resetsAtEpochSec),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.source_label, limits.sourceLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
                expiresLabel?.let {
                    Text(
                        text = stringResource(R.string.token_expires_at, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> {
                Text(
                    text = errorMessage ?: stringResource(R.string.credentials_missing),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.short_press_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.long_press_hint),
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = onActivateToy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.activate_toy))
        }
    }
}