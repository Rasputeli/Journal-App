package com.example.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SetupPasswordScreen(
    error: String?,
    busy: Boolean,
    onSetup: (String, String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            Text("Create your password", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your entries are encrypted with this password. It is never stored anywhere, " +
                        "so if you forget it the journal cannot be recovered.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            PasswordField(password, { password = it }, "Password")
            Spacer(Modifier.height(12.dp))
            PasswordField(
                confirm,
                { confirm = it },
                "Confirm password",
                imeAction = ImeAction.Done,
                onDone = { if (!busy) onSetup(password, confirm) },
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Button(
                    onClick = { onSetup(password, confirm) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create journal")
                }
            }
        }
    }
}

@Composable
fun UnlockScreen(
    error: String?,
    busy: Boolean,
    onUnlock: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            Text("Journal locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))

            PasswordField(
                password,
                { password = it },
                "Password",
                imeAction = ImeAction.Done,
                onDone = { if (!busy) onUnlock(password) },
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Button(
                    onClick = { onUnlock(password) },
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}
