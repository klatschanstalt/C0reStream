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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var restoreState by remember { mutableStateOf<RestoreResult?>(null) }
    // Kept only while a settings-only retry is possible. The copy is wiped as soon as it is
    // consumed or the screen leaves composition; the original password is wiped in `finally`.
    var retryEnvelope by remember { mutableStateOf<ByteArray?>(null) }
    var retrySecret by remember { mutableStateOf<CharArray?>(null) }

    fun clearRetry() {
        retrySecret?.fill('\u0000')
        retrySecret = null
        retryEnvelope = null
    }
    DisposableEffect(Unit) {
        onDispose { clearRetry() }
    }

    val result = restoreState
    val stateRes = when (result?.outcome) {
        RestoreOutcome.SUCCESS -> R.string.backup_restore_state_success
        RestoreOutcome.PARTIAL -> R.string.backup_restore_state_partial
        RestoreOutcome.FAILED -> R.string.backup_restore_state_failed
        RestoreOutcome.CANCELLED -> R.string.backup_restore_state_cancelled
        null -> null
    }
    val libraryRes = when (result?.libraryStatus) {
        RestorePartStatus.RESTORED -> R.string.backup_restore_part_library_restored
        RestorePartStatus.FAILED, RestorePartStatus.CANCELLED -> R.string.backup_restore_part_library_failed
        RestorePartStatus.NOT_APPLICABLE, null -> R.string.backup_restore_part_library_skipped
    }
    val settingsRes = when (result?.settingsStatus) {
        RestorePartStatus.RESTORED -> R.string.backup_restore_part_settings_restored
        RestorePartStatus.FAILED, RestorePartStatus.CANCELLED -> R.string.backup_restore_part_settings_failed
        RestorePartStatus.NOT_APPLICABLE, null -> R.string.backup_restore_part_settings_absent
    }
    val libraryText = if (result?.libraryStatus == RestorePartStatus.RESTORED) {
        stringResource(libraryRes, result.playlists.toString(), result.channels.toString())
    } else {
        stringResource(libraryRes)
    }
    val statusText = listOfNotNull(
        statusRes?.let { stringResource(it) },
        stateRes?.let { stringResource(it) },
        if (result != null) libraryText else null,
        if (result != null) stringResource(settingsRes) else null,
    ).joinToString("\n")

    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            selected = uri
            operation = "export"
            clearRetry()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = uri
            operation = "restore"
            clearRetry()
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
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (retrySecret != null && retryEnvelope != null && !busy) {
            Text(
                stringResource(R.string.backup_restore_retry_settings_note),
                style = MaterialTheme.typography.bodySmall,
            )
            BrandSecondaryButton(
                text = stringResource(R.string.backup_restore_retry_settings),
                onClick = {
                    val envelope = retryEnvelope ?: return@BrandSecondaryButton
                    val secretCopy = retrySecret?.copyOf() ?: return@BrandSecondaryButton
                    busy = true
                    scope.launch {
                        try {
                            val retried = LibraryBackup.restoreSettingsOnly(app.settings, envelope, secretCopy)
                            restoreState = retried
                            if (!retried.settingsRetryable) clearRetry()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            statusRes = R.string.backup_failed
                        } finally {
                            secretCopy.fill('\u0000')
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
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
                        restoreState = null
                        clearRetry()
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
                                    restoreState = restored
                                    if (restored.settingsRetryable) {
                                        retryEnvelope = bytes
                                        retrySecret = secret.copyOf()
                                    }
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
