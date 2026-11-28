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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.data.profiles.DataStorePinSession
import pw.c0re.stream.data.profiles.PinFailure
import pw.c0re.stream.data.profiles.PinOperationResult
import pw.c0re.stream.data.profiles.ProfileEntity
import pw.c0re.stream.data.profiles.ProfilePinOperations
import pw.c0re.stream.data.profiles.ProfileSessionRepository
import pw.c0re.stream.data.profiles.ProfileUiModel
import pw.c0re.stream.data.profiles.RoomProfilePinStore
import pw.c0re.stream.ui.components.BrandBackdrop
import pw.c0re.stream.ui.components.BrandBadge
import pw.c0re.stream.ui.components.BrandCard
import pw.c0re.stream.ui.components.BrandPrimaryButton
import pw.c0re.stream.ui.components.BrandSecondaryButton
import pw.c0re.stream.ui.components.BrandTopBar
import pw.c0re.stream.ui.components.rememberResponsivePadding

data class PinOperationUiState(
    val busy: Boolean = false,
    val failure: PinFailure? = null,
)

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as C0reStreamApp).database.profileDao()
    private val session = ProfileSessionRepository(app)
    private val pinManager = ProfileSessionRepository.pinManager
    private val operations = ProfilePinOperations(
        store = RoomProfilePinStore(dao),
        session = DataStorePinSession(session),
        pins = pinManager,
    )
    private val _operation = MutableStateFlow(PinOperationUiState())
    val operation: StateFlow<PinOperationUiState> = _operation.asStateFlow()

    val profiles = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeId = session.activeId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun add(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        dao.upsert(ProfileEntity(name = name.take(64), createdAt = System.currentTimeMillis()))
    }

    suspend fun setPin(
        id: Long,
        currentPin: String,
        newPin: String,
        confirmation: String,
    ): PinOperationResult = runOperation {
        val current = currentPin.toCharArray()
        val next = newPin.toCharArray()
        val confirm = confirmation.toCharArray()
        try {
            operations.changePin(id, current, next, confirm)
        } finally {
            wipe(current, next, confirm)
        }
    }

    suspend fun removePin(id: Long, currentPin: String): PinOperationResult = runOperation {
        val current = currentPin.toCharArray()
        try {
            operations.removePin(id, current)
        } finally {
            wipe(current)
        }
    }

    suspend fun switchProfile(id: Long, pin: String): PinOperationResult = runOperation {
        val chars = pin.toCharArray()
        try {
            operations.unlockAndSwitch(id, chars)
        } finally {
            wipe(chars)
        }
    }

    fun clearError() {
        if (!_operation.value.busy) _operation.value = PinOperationUiState()
    }

    private suspend fun runOperation(block: suspend () -> PinOperationResult): PinOperationResult {
        if (_operation.value.busy) {
            _operation.value = PinOperationUiState(failure = PinFailure.BUSY)
            return PinOperationResult.Failure(PinFailure.BUSY)
        }
        _operation.value = PinOperationUiState(busy = true)
        val result = try {
            block()
        } finally {
            _operation.value = PinOperationUiState()
        }
        if (result is PinOperationResult.Failure) {
            _operation.value = PinOperationUiState(failure = result.reason)
        }
        return result
    }

    private fun wipe(vararg pins: CharArray) = pins.forEach { it.fill('\u0000') }
}

private enum class PinDialogAction { SET, CHANGE, REMOVE }

@Composable
fun ProfilesScreen(onDone: () -> Unit, vm: ProfilesViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val operation by vm.operation.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<Pair<ProfileUiModel, PinDialogAction>?>(null) }
    var switchTarget by remember { mutableStateOf<ProfileUiModel?>(null) }
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
                                    if (profile.hasPin) {
                                        BrandSecondaryButton(
                                            text = stringResource(R.string.pin_action_change),
                                            onClick = {
                                                vm.clearError()
                                                pinTarget = profile to PinDialogAction.CHANGE
                                            },
                                            enabled = !operation.busy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        )
                                        BrandSecondaryButton(
                                            text = stringResource(R.string.profiles_pin_remove),
                                            onClick = {
                                                vm.clearError()
                                                pinTarget = profile to PinDialogAction.REMOVE
                                            },
                                            enabled = !operation.busy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        )
                                    } else {
                                        BrandSecondaryButton(
                                            text = stringResource(R.string.profiles_pin_set),
                                            onClick = {
                                                vm.clearError()
                                                pinTarget = profile to PinDialogAction.SET
                                            },
                                            enabled = !operation.busy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        )
                                    }
                                    BrandSecondaryButton(
                                        text = stringResource(R.string.profiles_switch),
                                        onClick = {
                                            vm.clearError()
                                            switchTarget = profile
                                        },
                                        enabled = !operation.busy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    )
                                }
                            }
                        }
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

    pinTarget?.let { target ->
        val (profile, action) = target
        val currentState = rememberTextFieldState()
        val newState = rememberTextFieldState()
        val confirmState = rememberTextFieldState()
        fun clearInputs() {
            currentState.clearText()
            newState.clearText()
            confirmState.clearText()
        }
        AlertDialog(
            onDismissRequest = {
                // Cancellation never reaches the business logic: there is no mutation to undo.
                clearInputs()
                vm.clearError()
                pinTarget = null
            },
            title = {
                Text(
                    stringResource(
                        when (action) {
                            PinDialogAction.SET -> R.string.profiles_pin_set
                            PinDialogAction.CHANGE -> R.string.pin_action_change
                            PinDialogAction.REMOVE -> R.string.profiles_pin_remove
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action != PinDialogAction.SET) {
                        OutlinedSecureTextField(
                            state = currentState,
                            inputTransformation = pinInputTransformation(),
                            label = { Text(stringResource(R.string.pin_current_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (action != PinDialogAction.REMOVE) {
                        OutlinedSecureTextField(
                            state = newState,
                            inputTransformation = pinInputTransformation(),
                            label = { Text(stringResource(R.string.pin_new_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedSecureTextField(
                            state = confirmState,
                            inputTransformation = pinInputTransformation(),
                            label = { Text(stringResource(R.string.pin_confirm_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    operation.failure?.let { failure ->
                        Text(
                            failure.message(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    enabled = !operation.busy,
                    onClick = {
                        val current = currentState.text.toString()
                        val next = newState.text.toString()
                        val confirm = confirmState.text.toString()
                        scope.launch {
                            val result = when (action) {
                                PinDialogAction.SET -> vm.setPin(profile.id, "", next, confirm)
                                PinDialogAction.CHANGE -> vm.setPin(profile.id, current, next, confirm)
                                PinDialogAction.REMOVE -> vm.removePin(profile.id, current)
                            }
                            if (result == PinOperationResult.Success) {
                                clearInputs()
                                pinTarget = null
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
                        clearInputs()
                        vm.clearError()
                        pinTarget = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }

    switchTarget?.let { profile ->
        val pinState = rememberTextFieldState()
        AlertDialog(
            onDismissRequest = {
                pinState.clearText()
                vm.clearError()
                switchTarget = null
            },
            title = { Text(stringResource(R.string.profiles_switch)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedSecureTextField(
                        state = pinState,
                        inputTransformation = pinInputTransformation(),
                        label = { Text(stringResource(R.string.profiles_pin_prompt)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    operation.failure?.let { failure ->
                        Text(
                            failure.message(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                BrandPrimaryButton(
                    text = stringResource(R.string.action_confirm),
                    enabled = !operation.busy,
                    onClick = {
                        val value = pinState.text.toString()
                        pinState.clearText()
                        scope.launch {
                            if (vm.switchProfile(profile.id, value) == PinOperationResult.Success) {
                                switchTarget = null
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
                        pinState.clearText()
                        vm.clearError()
                        switchTarget = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }
}

private fun pinInputTransformation(): InputTransformation =
    InputTransformation.maxLengthReject(64).byValue { input, _ -> input.filter(Char::isDigit) }

@Composable
private fun PinFailure.message(): String = stringResource(
    when (this) {
        PinFailure.WRONG_CURRENT -> R.string.pin_error_wrong_current
        PinFailure.MISMATCHED_NEW -> R.string.pin_error_mismatch
        PinFailure.INVALID_FORMAT -> R.string.pin_error_invalid
        PinFailure.TOO_MANY_ATTEMPTS -> R.string.pin_error_locked
        PinFailure.UNREADABLE_RECORD -> R.string.pin_error_unavailable
        PinFailure.PROFILE_UNAVAILABLE -> R.string.pin_error_unavailable
        PinFailure.PROFILE_CHANGED -> R.string.pin_error_profile_changed
        PinFailure.BUSY -> R.string.pin_error_busy
        PinFailure.CANCELLED -> R.string.pin_error_busy
    },
)
