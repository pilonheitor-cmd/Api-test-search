package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConsultaRecord
import com.example.data.model.LookupUiState
import com.example.data.model.SearchMode
import com.example.data.model.UiState
import com.example.data.remote.CepResponse
import com.example.data.remote.CnpjResponse
import com.example.data.remote.CpfValidator
import com.example.data.remote.DddResponse
import com.example.data.remote.IbgeNomeResponse
import com.example.data.repository.ConsultaRepository
import com.example.data.repository.PublicDataLookupRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ThinkingUiState {
    data object Idle : ThinkingUiState
    data class Streaming(val text: String) : ThinkingUiState
    data class Success(val text: String) : ThinkingUiState
    data class Error(val message: String) : ThinkingUiState
}

sealed interface PublicToolState<out T> {
    data object Idle : PublicToolState<Nothing>
    data object Loading : PublicToolState<Nothing>
    data class Success<T>(val data: T) : PublicToolState<T>
    data class Error(val message: String) : PublicToolState<Nothing>
}

class ConsultaViewModel(
    private val consultaRepository: ConsultaRepository,
    private val lookupRepository: PublicDataLookupRepository,
    val currentUserId: String
) : ViewModel() {

    companion object {
        private const val TAG = "ConsultaVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }

    // Two-tier Flow error handling for Firestore snapshot listener
    val consultasState: StateFlow<UiState<List<ConsultaRecord>>> =
        consultaRepository.observeConsultas(currentUserId)
            .map<List<ConsultaRecord>, UiState<List<ConsultaRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Erro ao observar consultas no Firestore", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Falha ao carregar histórico do Firestore."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _lookupState = MutableStateFlow<LookupUiState>(LookupUiState.Idle)
    val lookupState: StateFlow<LookupUiState> = _lookupState.asStateFlow()

    private val _thinkingState = MutableStateFlow<ThinkingUiState>(ThinkingUiState.Idle)
    val thinkingState: StateFlow<ThinkingUiState> = _thinkingState.asStateFlow()

    private val _cnpjToolState = MutableStateFlow<PublicToolState<CnpjResponse>>(PublicToolState.Idle)
    val cnpjToolState: StateFlow<PublicToolState<CnpjResponse>> = _cnpjToolState.asStateFlow()

    private val _cepToolState = MutableStateFlow<PublicToolState<CepResponse>>(PublicToolState.Idle)
    val cepToolState: StateFlow<PublicToolState<CepResponse>> = _cepToolState.asStateFlow()

    private val _dddToolState = MutableStateFlow<PublicToolState<DddResponse>>(PublicToolState.Idle)
    val dddToolState: StateFlow<PublicToolState<DddResponse>> = _dddToolState.asStateFlow()

    private val _ibgeToolState = MutableStateFlow<PublicToolState<List<IbgeNomeResponse>>>(PublicToolState.Idle)
    val ibgeToolState: StateFlow<PublicToolState<List<IbgeNomeResponse>>> = _ibgeToolState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun syncUserProfile(user: FirebaseUser) {
        viewModelScope.launch {
            consultaRepository.ensureUserProfile(user)
        }
    }

    fun executeConsulta(
        mode: SearchMode,
        nameInput: String,
        cpfInput: String,
        phoneInput: String,
        onSavedNavigate: (String) -> Unit
    ) {
        val cleanName = nameInput.trim()
        val cpfDigits = CpfValidator.digitsOnly(cpfInput)
        val phoneDigits = CpfValidator.digitsOnly(phoneInput)

        val validationError = when (mode) {
            SearchMode.NAME -> if (cleanName.length < 3) "Digite o nome completo (mínimo 3 caracteres)." else null
            SearchMode.CPF -> if (cpfDigits.length != 11) "Digite um CPF com 11 dígitos." else null
            SearchMode.COMBINED -> when {
                cleanName.length < 3 -> "No modo combinado, informe o nome completo."
                cpfDigits.length != 11 -> "No modo combinado, informe o CPF com 11 dígitos."
                phoneDigits.length < 10 -> "No modo combinado, informe o telefone com DDD."
                else -> null
            }
        }

        if (validationError != null) {
            _lookupState.value = LookupUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _lookupState.value = LookupUiState.Searching("Iniciando validação e conexão com APIs...", 0.1f)
            try {
                val record = lookupRepository.performFullConsulta(
                    userId = currentUserId,
                    mode = mode,
                    nameInput = cleanName,
                    cpfInput = cpfInput,
                    phoneInput = phoneInput,
                    onStepUpdate = { label, fraction ->
                        _lookupState.value = LookupUiState.Searching(label, fraction)
                    }
                )

                val saveResult = consultaRepository.saveConsulta(record)
                saveResult.fold(
                    onSuccess = { newId ->
                        val savedRecord = record.copy(id = newId)
                        _lookupState.value = LookupUiState.Success(savedRecord)
                        onSavedNavigate(newId)
                    },
                    onFailure = { err ->
                        _lookupState.value = LookupUiState.Error(
                            "Consulta realizada, mas erro ao salvar no Firestore: ${err.localizedMessage ?: "Erro"}"
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante consulta pública", e)
                _lookupState.value = LookupUiState.Error(
                    e.localizedMessage ?: "Erro inesperado ao executar a consulta."
                )
            }
        }
    }

    fun runDeepThinking(record: ConsultaRecord) {
        viewModelScope.launch {
            _thinkingState.value = ThinkingUiState.Streaming("Ativando Gemini 4 Carbon (ThinkingLevel.HIGH)...")
            try {
                var finalAnalysis = ""
                lookupRepository.streamDeepThinkingAnalysis(record).collect { partial ->
                    finalAnalysis = partial
                    _thinkingState.value = ThinkingUiState.Streaming(partial)
                }
                _thinkingState.value = ThinkingUiState.Success(finalAnalysis)
                if (record.id.isNotBlank()) {
                    consultaRepository.updateThinkingAnalysis(
                        userId = currentUserId,
                        consultaId = record.id,
                        summary = record.summary,
                        thinkingAnalysis = finalAnalysis
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Erro no Gemini 4 Carbon Thinking", e)
                _thinkingState.value = ThinkingUiState.Error(
                    e.localizedMessage ?: "Falha na análise profunda."
                )
            }
        }
    }

    fun resetLookupState() {
        _lookupState.value = LookupUiState.Idle
    }

    fun resetThinkingState() {
        _thinkingState.value = ThinkingUiState.Idle
    }

    fun togglePinned(record: ConsultaRecord) {
        if (record.id.isBlank()) return
        viewModelScope.launch {
            val result = consultaRepository.togglePinned(currentUserId, record.id, !record.pinned)
            result.onFailure {
                _actionMessage.value = "Não foi possível atualizar favorito."
            }
        }
    }

    fun deleteConsulta(consultaId: String, onDeleted: () -> Unit = {}) {
        if (consultaId.isBlank()) return
        viewModelScope.launch {
            val result = consultaRepository.deleteConsulta(currentUserId, consultaId)
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Consulta removida do histórico."
                    onDeleted()
                },
                onFailure = {
                    _actionMessage.value = "Erro ao remover consulta."
                }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    // --- Direct Public API Tools ---
    fun queryCnpj(cnpj: String) {
        viewModelScope.launch {
            _cnpjToolState.value = PublicToolState.Loading
            lookupRepository.lookupCnpj(cnpj).fold(
                onSuccess = { _cnpjToolState.value = PublicToolState.Success(it) },
                onFailure = { _cnpjToolState.value = PublicToolState.Error(it.localizedMessage ?: "CNPJ não encontrado na BrasilAPI.") }
            )
        }
    }

    fun queryCep(cep: String) {
        viewModelScope.launch {
            _cepToolState.value = PublicToolState.Loading
            lookupRepository.lookupCep(cep).fold(
                onSuccess = { _cepToolState.value = PublicToolState.Success(it) },
                onFailure = { _cepToolState.value = PublicToolState.Error(it.localizedMessage ?: "CEP não encontrado na BrasilAPI.") }
            )
        }
    }

    fun queryDdd(ddd: String) {
        viewModelScope.launch {
            _dddToolState.value = PublicToolState.Loading
            lookupRepository.lookupDdd(ddd).fold(
                onSuccess = { _dddToolState.value = PublicToolState.Success(it) },
                onFailure = { _dddToolState.value = PublicToolState.Error(it.localizedMessage ?: "DDD não encontrado na BrasilAPI.") }
            )
        }
    }

    fun queryIbgeName(name: String) {
        viewModelScope.launch {
            _ibgeToolState.value = PublicToolState.Loading
            lookupRepository.lookupIbgeName(name).fold(
                onSuccess = { _ibgeToolState.value = PublicToolState.Success(it) },
                onFailure = { _ibgeToolState.value = PublicToolState.Error(it.localizedMessage ?: "Nome não encontrado no IBGE.") }
            )
        }
    }
}
