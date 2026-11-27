package pw.c0re.stream.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pw.c0re.stream.C0reStreamApp
import java.io.ByteArrayOutputStream

@Composable
fun BackupControls() {
    val context = LocalContext.current
    val db = (context.applicationContext as C0reStreamApp).database
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { selected = uri; operation = "export" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { selected = uri; operation = "restore" }
    }
    Column {
        TextButton(enabled = !busy, onClick = { create.launch("C0reStream.c0backup") }) { Text("Verschlüsseltes Backup erstellen") }
        TextButton(enabled = !busy, onClick = { open.launch(arrayOf("application/octet-stream", "application/*")) }) { Text("Backup wiederherstellen") }
        if (busy) LinearProgressIndicator()
        if (status.isNotEmpty()) Text(status)
    }
    if (operation != null) AlertDialog(
        onDismissRequest = { operation = null; selected = null; password = "" },
        title = { Text(if (operation == "export") "Backup verschlüsseln" else "Backup importieren") },
        text = {
            Column {
                Text(if (operation == "export") "Mindestens 12 Zeichen. Ohne Passwort ist keine Wiederherstellung möglich." else "Import ergänzt die vorhandene Bibliothek. Duplikate sind möglich.")
                OutlinedTextField(password, { if (it.length <= 1024) password = it }, label = { Text("Passwort") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(enabled = password.length >= 12, onClick = {
                val uri = selected ?: return@TextButton
                val exporting = operation == "export"
                val secret = password.toCharArray()
                password = ""; selected = null; operation = null; busy = true; status = ""
                scope.launch {
                    try {
                        if (exporting) {
                            val bytes = LibraryBackup.export(db, secret)
                            withContext(Dispatchers.IO) {
                                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                            }
                            status = "Backup gespeichert."
                        } else {
                            val bytes = withContext(Dispatchers.IO) {
                                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                                    val out = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        require(out.size() + n <= 32 * 1024 * 1024 + 52)
                                        out.write(buffer, 0, n)
                                    }
                                    out.toByteArray()
                                }
                            }
                            val count = LibraryBackup.restore(db, bytes, secret)
                            status = "$count Playlists importiert."
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Backup fehlgeschlagen. Datei, Passwort und Speicher prüfen." }
                    finally { secret.fill('\u0000'); busy = false }
                }
            }) { Text("Bestätigen") }
        },
        dismissButton = { TextButton(onClick = { operation = null; selected = null; password = "" }) { Text("Abbrechen") } }
    )
}
