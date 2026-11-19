package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SearchMode(val labelPt: String, val subtitlePt: String) {
    NAME("Nome Completo", "Estatísticas IBGE + Busca Pública"),
    CPF("CPF", "Validação Receita + Busca Pública"),
    COMBINED("Completo + Tel", "Nome, CPF e Telefone (DDD)")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DossierFields(
    val nome: String = "",
    val cpf: String = "",
    val nascimento: String = "",
    val score: String = "",
    val nomeMae: String = "",
    val nomePai: String = "",
    val sexo: String = "",
    val estadoCivil: String = "",
    val escolaridade: String = "",
    val rendaMensal: String = "",
    val rg: String = "",
    val enderecoPrincipal: String = "",
    val enderecoSecundario: String = "",
    val telefone: String = "",
    val email: String = "",
    val profissao: String = "",
    val veiculo: String = "",
    val parentes: String = "",
    val vizinhos: String = ""
) {
    fun toFirestoreMap(): Map<String, String> = mapOf(
        "nome" to nome.take(600),
        "cpf" to cpf.take(600),
        "nascimento" to nascimento.take(600),
        "score" to score.take(600),
        "nomeMae" to nomeMae.take(600),
        "nomePai" to nomePai.take(600),
        "sexo" to sexo.take(600),
        "estadoCivil" to estadoCivil.take(600),
        "escolaridade" to escolaridade.take(600),
        "rendaMensal" to rendaMensal.take(600),
        "rg" to rg.take(600),
        "enderecoPrincipal" to enderecoPrincipal.take(600),
        "enderecoSecundario" to enderecoSecundario.take(600),
        "telefone" to telefone.take(600),
        "email" to email.take(600),
        "profissao" to profissao.take(600),
        "veiculo" to veiculo.take(600),
        "parentes" to parentes.take(600),
        "vizinhos" to vizinhos.take(600)
    )
}

@Keep
@IgnoreExtraProperties
data class ConsultaRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val searchMode: String = SearchMode.NAME.name,
    val queryName: String = "",
    val queryCpf: String = "",
    val queryPhone: String = "",
    val cpfValid: Boolean = false,
    val cpfRegion: String = "",
    val dddRegion: String = "",
    val ibgeFrequency: Long = 0L,
    val summary: String = "",
    val thinkingAnalysis: String = "",
    val dossier: DossierFields = DossierFields(),
    val sourceEntries: List<String> = emptyList(),
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSearchMode(): SearchMode =
        enumValues<SearchMode>().firstOrNull { it.name == searchMode } ?: SearchMode.NAME

    fun parsedSources(): List<GroundedSource> =
        sourceEntries.mapNotNull { entry ->
            val parts = entry.split(" | ", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) {
                GroundedSource(title = parts[0].ifBlank { "Fonte Pública" }, url = parts[1])
            } else null
        }
}

data class GroundedSource(
    val title: String,
    val url: String
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface LookupUiState {
    data object Idle : LookupUiState
    data class Searching(val currentStep: String, val progressFraction: Float) : LookupUiState
    data class Success(val record: ConsultaRecord) : LookupUiState
    data class Error(val message: String) : LookupUiState
}
