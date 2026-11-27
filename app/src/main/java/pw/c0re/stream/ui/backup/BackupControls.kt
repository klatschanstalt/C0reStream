package pw.c0re.stream.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import java.io.ByteArrayOutputStream

@Composable
fun BackupControls() {
    val context = LocalContext.current
    val app = context.applicationContext as C0reStreamApp
    val db = app.database
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusRes by remember { mutableStateOf<Int?>(null) }
    var restoredCount by remember { mutableStateOf<Int?>(null) }
    val statusText = when {
        restoredCount != null -> pluralStringResource(
            R.plurals.backup_restored_playlists,
            restoredCount!!,
            restoredCount!!,
        )
        statusRes != null -> stringResource(statusRes!!)
        else -> ""
    }

    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            selected = uri
            operation = "export"
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = uri
            operation = "restore"
        }
    }

    Column {
        TextButton(enabled = !busy, onClick = { create.launch("C0reStream.c0backup") }) {
            Text(stringResource(R.string.backup_create))
        }
        TextButton(enabled = !busy, onClick = {
            open.launch(arrayOf("application/octet-stream", "application/*"))
        }) { Text(stringResource(R.string.backup_restore)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        if (statusText.isNotEmpty()) Text(statusText, Modifier.padding(vertical = 4.dp))
    }

    if (operation != null) {
        val exporting = operation == "export"
        AlertDialog(
            onDismissRequest = {
                operation = null
                selected = null
                password = ""
            },
            title = {
                Text(
                    stringResource(
                        if (exporting) R.string.backup_create else R.string.backup_restore,
                    ),
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.backup_password_hint))
                    Text(stringResource(R.string.backup_import_merge_note), Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (it.length <= 1_024) password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= 12 && !busy, onClick = {
                    val uri = selected ?: return@TextButton
                    val secret = password.toCharArray()
                    password = ""
                    selected = null
                    operation = null
                    busy = true
                    statusRes = null
                    restoredCount = null
                    scope.launch {
                        try {
                            if (exporting) {
                                val bytes = LibraryBackup.export(db, app.settings, secret)
                                withContext(Dispatchers.IO) {
                                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                        .use { it.write(bytes) }
                                }
                                statusRes = R.string.backup_saved
                            } else {
                                val bytes = withContext(Dispatchers.IO) {
                                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                                        val out = ByteArrayOutputStream()
                                        val buffer = ByteArray(8_192)
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read < 0) break
                                            require(out.size() + read <= 32 * 1024 * 1024 + 64)
                                            out.write(buffer, 0, read)
                                        }
                                        out.toByteArray()
                                    }
                                }
                                val restored = LibraryBackup.restore(db, app.settings, bytes, secret)
                                restoredCount = restored.playlists
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            statusRes = R.string.backup_failed
                        } finally {
                            secret.fill('\u0000')
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    operation = null
                    selected = null
                    password = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
