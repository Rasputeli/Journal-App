package com.example.journal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change password") },
        text = {
            Column {
                Text(
                    "Every entry and picture is decrypted and re-encrypted with the new " +
                            "password. Keep the app open until this finishes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                PasswordField(current, { current = it }, "Current password")
                Spacer(Modifier.height(12.dp))
                PasswordField(newPassword, { newPassword = it }, "New password")
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    confirm,
                    { confirm = it },
                    "Confirm new password",
                    imeAction = ImeAction.Done,
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && current.isNotBlank() && newPassword.isNotBlank(),
                onClick = {
                    if (newPassword != confirm) {
                        error = "New passwords don't match."
                        return@TextButton
                    }
                    busy = true
                    error = null
                    onSubmit(current, newPassword) { result ->
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text("Change") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

@Composable
fun ImportPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open this backup") },
        text = {
            Column {
                Text(
                    "Backups are encrypted. Enter the journal password that was in " +
                            "use when this backup was made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password for this backup",
                    imeAction = ImeAction.Done,
                    onDone = { if (password.isNotBlank()) onConfirm(password) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank(),
                onClick = { onConfirm(password) },
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun UnlockEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, (Boolean) -> Unit) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("This entry is hidden") },
        text = {
            Column {
                Text(
                    "Enter your journal password to read it. It is hidden from the list, " +
                            "from search snippets and from the On this day cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Password",
                    imeAction = ImeAction.Done,
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotBlank(),
                onClick = {
                    busy = true
                    onConfirm(password) { ok ->
                        busy = false
                        if (ok) onDismiss() else error = "Wrong password."
                    }
                },
            ) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
