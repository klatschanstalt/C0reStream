package pw.c0re.stream.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import java.io.ByteArrayOutputStream

@Composable
fun BackupControls() {
    val context = LocalContext.current
    val app = context.applicationContext as C0reStreamApp
    val db = app.database
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val passwordState = rememberTextFieldState()
    val password by remember { derivedStateOf { passwordState.text.toString() } }
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

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandPrimaryButton(
            text = stringResource(R.string.backup_create),
            onClick = { create.launch("C0reStream.c0backup") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        BrandSecondaryButton(
            text = stringResource(R.string.backup_restore),
            onClick = { open.launch(arrayOf("application/octet-stream", "application/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (operation != null) {
        val exporting = operation == "export"
        AlertDialog(
            onDismissRequest = {
                operation = null
                selected = null
                passwordState.clearText()
            },
            title = {
                Text(
                    stringResource(
                        if (exporting) R.string.backup_create else R.string.backup_restore,
                    ),
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.backup_import_merge_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedSecureTextField(
                        state = passwordState,
                        inputTransformation = InputTransformation.maxLengthReject(1_024),
                        label = { Text(stringResource(R.string.backup_password)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    enabled = password.length >= 12 && !busy,
                    onClick = {
                        val uri = selected ?: return@BrandPrimaryButton
                        val secret = password.toCharArray()
                        passwordState.clearText()
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
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            dismissButton = {
                BrandSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        operation = null
                        selected = null
                        passwordState.clearText()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }
}
