package pw.c0re.stream.ui.profiles

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.profiles.PinVerification
import pw.c0re.stream.data.profiles.ProfileEntity
import pw.c0re.stream.data.profiles.ProfileSessionRepository
import pw.c0re.stream.data.profiles.ProfileUiModel
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.rememberResponsivePadding

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = rememberResponsivePadding().coerceAtMost(maxWidth / 8)
        BrandBackdrop(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The top bar back action is the former "Close" button callback.
                BrandTopBar(
                    title = stringResource(R.string.profiles_title),
                    onBack = onDone,
                    actions = {},
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(profiles.map { ProfileUiModel.from(it) }, key = { it.id }) { profile ->
                        BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (profile.id == activeId) {
                                        BrandBadge(text = stringResource(R.string.profiles_active))
                                    }
                                    if (profile.hasPin) {
                                        BrandBadge(text = "PIN")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BrandSecondaryButton(
                                        text = stringResource(
                                            if (profile.hasPin) R.string.profiles_pin_remove else R.string.profiles_pin_set,
                                        ),
                                        onClick = { pinTarget = profile },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    )
                                    BrandSecondaryButton(
                                        text = stringResource(R.string.profiles_switch),
                                        onClick = { switchTarget = profile },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (error) {
                    BrandCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                        Text(
                            stringResource(R.string.profiles_pin_wrong),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                BrandPrimaryButton(
                    text = stringResource(R.string.profiles_add),
                    onClick = { addDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        vm.add(name)
                        addDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            dismissButton = {
                BrandSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { addDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }

    pinTarget?.let { profile ->
        val pinState = rememberTextFieldState()
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = {
                Text(stringResource(if (profile.hasPin) R.string.profiles_pin_remove else R.string.profiles_pin_set))
            },
            text = {
                OutlinedSecureTextField(
                    state = pinState,
                    inputTransformation = InputTransformation.maxLengthReject(64)
                        .byValue { input, _ -> input.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.profiles_pin_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val value = pinState.text.toString()
                        vm.setPin(profile.id, if (value.length in 4..64) value else "")
                        pinState.clearText()
                        pinTarget = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            dismissButton = {
                BrandSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { pinTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }

    switchTarget?.let { profile ->
        val pinState = rememberTextFieldState()
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text(stringResource(R.string.profiles_switch)) },
            text = {
                OutlinedSecureTextField(
                    state = pinState,
                    inputTransformation = InputTransformation.maxLengthReject(64)
                        .byValue { input, _ -> input.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.profiles_pin_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val value = pinState.text.toString()
                        pinState.clearText()
                        switchTarget = null
                        scope.launch { error = !vm.unlock(profile.id, value) }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            dismissButton = {
                BrandSecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { switchTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }
}
