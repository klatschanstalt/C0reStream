package pw.c0re.stream.ui.profiles

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.profiles.PinManager
import pw.c0re.stream.data.profiles.PinVerification
import pw.c0re.stream.data.profiles.ProfileEntity
import pw.c0re.stream.data.profiles.ProfileSessionRepository
import pw.c0re.stream.data.profiles.ProfileUiModel

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as C0reStreamApp).database.profileDao()
    private val session = ProfileSessionRepository(app)
    private val pinManager = ProfileSessionRepository.pinManager

    val profiles = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeId = session.activeId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun add(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        dao.upsert(ProfileEntity(name = name.take(64), createdAt = System.currentTimeMillis()))
    }

    fun setPin(id: Long, pin: String) = viewModelScope.launch {
        val entity = dao.byId(id) ?: return@launch
        val chars = pin.toCharArray()
        val record = if (chars.size in 4..64) pinManager.hash(chars) else null
        chars.fill('\u0000')
        dao.upsert(entity.copy(pinHash = record))
        pinManager.reset(id)
    }

    suspend fun unlock(id: Long, pin: String): Boolean {
        val entity = dao.byId(id) ?: return false
        val chars = pin.toCharArray()
        val result = try {
            pinManager.verify(entity.pinHash, chars, id)
        } finally {
            chars.fill('\u0000')
        }
        if (result == PinVerification.Success || result == PinVerification.NoPin) {
            session.setActive(id)
            return true
        }
        return false
    }
}

@Composable
fun ProfilesScreen(onDone: () -> Unit, vm: ProfilesViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<ProfileUiModel?>(null) }
    var switchTarget by remember { mutableStateOf<ProfileUiModel?>(null) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles.map { ProfileUiModel.from(it) }, key = { it.id }) { profile ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        if (profile.id == activeId) {
                            Text(stringResource(R.string.profiles_active), style = MaterialTheme.typography.bodySmall)
                        }
                        if (profile.hasPin) {
                            Text("PIN", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pinTarget = profile }) {
                                Text(
                                    stringResource(
                                        if (profile.hasPin) R.string.profiles_pin_remove else R.string.profiles_pin_set,
                                    ),
                                )
                            }
                            TextButton(onClick = { switchTarget = profile }) {
                                Text(stringResource(R.string.profiles_switch))
                            }
                        }
                    }
                }
            }
        }
        if (error) Text(stringResource(R.string.profiles_pin_wrong), color = MaterialTheme.colorScheme.error)
        Button(onClick = { addDialog = true }) { Text(stringResource(R.string.profiles_add)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_close)) }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text(stringResource(R.string.profiles_add)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(64) },
                    label = { Text(stringResource(R.string.profiles_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.add(name)
                    addDialog = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { addDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pinTarget?.let { profile ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text(stringResource(if (profile.hasPin) R.string.profiles_pin_remove else R.string.profiles_pin_set)) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(64) },
                    label = { Text(stringResource(R.string.profiles_pin_prompt)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPin(profile.id, if (pin.length in 4..64) pin else "")
                    pin = ""
                    pinTarget = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pinTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    switchTarget?.let { profile ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text(stringResource(R.string.profiles_switch)) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(64) },
                    label = { Text(stringResource(R.string.profiles_pin_prompt)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = pin
                    pin = ""
                    switchTarget = null
                    scope.launch { error = !vm.unlock(profile.id, value) }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { switchTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
