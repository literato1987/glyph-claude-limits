package com.nothinglondon.sdkdemo

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nothinglondon.sdkdemo.claude.ClaudeLimits
import com.nothinglondon.sdkdemo.claude.ClaudeLimitsProvider
import com.nothinglondon.sdkdemo.claude.ClaudeUsageException
import com.nothinglondon.sdkdemo.claude.OAuthCredentials
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
                        refreshLimits = { ClaudeLimitsProvider.refresh(this) },
                        hasCredentials = { TokenStore.hasCredentials(this) },
                        loadCredentials = { TokenStore.loadCredentials(this) },
                        saveCredentials = { TokenStore.saveCredentials(this, it) },
                        clearCredentials = { TokenStore.clearCredentials(this) },
                    )
                }
            }
        }
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
    refreshLimits: suspend () -> ClaudeLimits,
    hasCredentials: () -> Boolean,
    loadCredentials: () -> OAuthCredentials?,
    saveCredentials: (OAuthCredentials) -> Unit,
    clearCredentials: () -> Unit,
) {
    var limits by remember { mutableStateOf(ClaudeLimits.EMPTY) }
    var isLoading by remember { mutableStateOf(true) }
    var credentialsInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var configured by remember { mutableStateOf(hasCredentials()) }
    var expiresLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    fun reload() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                limits = refreshLimits()
                loadCredentials()?.let { creds ->
                    expiresLabel = timeFormat.format(Date(creds.expiresAtMs))
                }
                if (limits.usedPercentage < 0) {
                    errorMessage = "No se pudieron obtener los límites"
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

    LaunchedEffect(configured) {
        if (configured) reload()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Claude Glyph Limits",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

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
                reload()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = credentialsInput.isNotBlank(),
        ) {
            Text(text = stringResource(R.string.save_token))
        }

        if (configured) {
            TextButton(onClick = {
                clearCredentials()
                credentialsInput = ""
                configured = false
                limits = ClaudeLimits.EMPTY
                expiresLabel = null
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
                    text = errorMessage ?: stringResource(R.string.sync_missing),
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